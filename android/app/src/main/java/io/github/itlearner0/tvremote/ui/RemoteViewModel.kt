package io.github.itlearner0.tvremote.ui

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.itlearner0.tvremote.data.AppCatalog
import io.github.itlearner0.tvremote.data.FoundTv
import io.github.itlearner0.tvremote.data.TvApp
import io.github.itlearner0.tvremote.data.TvDiscovery
import io.github.itlearner0.tvremote.protocol.ClientIdentity
import io.github.itlearner0.tvremote.protocol.InvalidCodeException
import io.github.itlearner0.tvremote.protocol.Keys
import io.github.itlearner0.tvremote.protocol.PairingRequiredException
import io.github.itlearner0.tvremote.protocol.PairingSession
import io.github.itlearner0.tvremote.protocol.RemoteSession
import io.github.itlearner0.tvremote.protocol.TvState
import io.github.itlearner0.tvremote.protocol.WakeOnLan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class Connection {
    /** Aucune TV choisie : écran de connexion. */
    NONE,
    CONNECTING,

    /** Code affiché sur la TV, en attente de saisie. */
    PAIRING,

    /** TV appairée mais éteinte ou injoignable. */
    OFFLINE,
    CONNECTED,
}

data class UiState(
    val connection: Connection = Connection.NONE,
    val host: String? = null,
    val mac: String? = null,
    val tv: TvState = TvState(),
    val error: String? = null,
    val discovering: Boolean = false,
    val discovered: List<FoundTv> = emptyList(),
    val apps: List<TvApp> = emptyList(),
    val appsComplete: Boolean = false,
    val scanningApps: Boolean = false,
    val appsMessage: String? = null,
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("tv", Context.MODE_PRIVATE)
    private val catalog = AppCatalog(prefs)
    private val discovery = TvDiscovery(application)
    private val identity by lazy { ClientIdentity.loadOrCreate(application.filesDir) }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Messages ponctuels (affichés en bas de l'écran). */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    @Volatile private var session: RemoteSession? = null
    private var pairing: PairingSession? = null
    private var keepAlive: Job? = null
    private var discoveryJob: Job? = null
    private var foreground = false

    init {
        val host = prefs.getString(KEY_HOST, null)
        _state.value = UiState(
            connection = if (host != null) Connection.OFFLINE else Connection.NONE,
            host = host,
            mac = prefs.getString(KEY_MAC, null),
            apps = catalog.list(),
            appsComplete = catalog.isComplete,
        )
    }

    fun consumeMessage() {
        _messages.value = null
    }

    private fun say(message: String) {
        _messages.value = message
    }

    // --- Cycle de vie : connexion seulement quand l'application est visible ---

    fun onForeground() {
        foreground = true
        if (_state.value.host != null && _state.value.connection != Connection.PAIRING) startKeepAlive()
    }

    fun onBackground() {
        foreground = false
        keepAlive?.cancel()
        keepAlive = null
        closeSession()
        _state.update { if (it.connection == Connection.CONNECTED) it.copy(connection = Connection.OFFLINE) else it }
    }

    // --- Choix de la TV et appairage ---

    fun discover() {
        discoveryJob?.cancel()
        _state.update { it.copy(discovering = true, discovered = emptyList(), error = null) }
        discoveryJob = viewModelScope.launch {
            withTimeoutOrNull(6_000) {
                discovery.discover().collect { tv ->
                    _state.update { s ->
                        if (s.discovered.any { it.host == tv.host }) s else s.copy(discovered = s.discovered + tv)
                    }
                }
            }
            _state.update { it.copy(discovering = false) }
        }
    }

    fun connect(host: String) {
        val target = host.trim()
        if (target.isEmpty()) {
            _state.update { it.copy(error = "Saisissez l'adresse IP de la TV") }
            return
        }
        keepAlive?.cancel()
        closeSession()
        closePairing()
        if (target != _state.value.host) {
            prefs.edit().remove(KEY_MAC).apply()
            _state.update { it.copy(mac = null) }
        }
        _state.update { it.copy(connection = Connection.CONNECTING, host = target, error = null) }
        viewModelScope.launch {
            try {
                openSession(target)
                prefs.edit().putString(KEY_HOST, target).apply()
                startKeepAlive()
            } catch (e: PairingRequiredException) {
                startPairing(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(connection = Connection.NONE, error = "Impossible de joindre la TV : vérifiez l'adresse et le Wi-Fi")
                }
            }
        }
    }

    private suspend fun startPairing(host: String) {
        closePairing()
        try {
            // La clé du téléphone est créée au premier usage : calcul long, hors du fil principal.
            pairing = withContext(Dispatchers.IO) {
                PairingSession(host, identity, clientName()).also { p ->
                    try {
                        p.start()
                    } catch (e: Exception) {
                        p.close()
                        throw e
                    }
                }
            }
            _state.update { it.copy(connection = Connection.PAIRING, host = host) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(connection = Connection.NONE, error = "La TV n'a pas lancé l'appairage. Réessayez.") }
        }
    }

    fun submitCode(code: String) {
        val p = pairing ?: return
        val host = _state.value.host ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { p.finish(code) }
                closePairing()
                _state.update { it.copy(connection = Connection.CONNECTING, error = null) }
                openSession(host)
                prefs.edit().putString(KEY_HOST, host).apply()
                startKeepAlive()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidCodeException) {
                _state.update { it.copy(error = "Code incorrect : un nouveau code va s'afficher sur la TV") }
                startPairing(host)
            } catch (e: Exception) {
                closePairing()
                _state.update { it.copy(connection = Connection.NONE, error = "Appairage interrompu, recommencez") }
            }
        }
    }

    fun cancelPairing() {
        closePairing()
        _state.update { it.copy(connection = if (prefs.contains(KEY_HOST)) Connection.OFFLINE else Connection.NONE, error = null) }
    }

    /** Oublie la TV (pour en choisir une autre). */
    fun forgetTv() {
        keepAlive?.cancel()
        closeSession()
        closePairing()
        prefs.edit().remove(KEY_HOST).remove(KEY_MAC).apply()
        catalog.clear()
        _state.value = UiState(apps = catalog.list())
    }

    // --- Connexion permanente ---

    private suspend fun openSession(host: String) {
        val s = withContext(Dispatchers.IO) {
            RemoteSession(
                host = host,
                identity = identity,
                onState = ::onTvState,
                onClosed = { onSessionClosed() },
            ).also { it.connect() }
        }
        session = s
        val mac = _state.value.mac ?: s.macAddress?.also { prefs.edit().putString(KEY_MAC, it).apply() }
        _state.update { it.copy(connection = Connection.CONNECTED, tv = s.state, mac = mac, error = null) }
        onTvState(s.state)
    }

    private fun onTvState(tv: TvState) {
        if (tv.currentApp.isNotEmpty() && catalog.remember(tv.currentApp)) {
            _state.update { it.copy(apps = catalog.list()) }
        }
        _state.update { if (it.connection == Connection.CONNECTED) it.copy(tv = tv) else it }
    }

    private fun onSessionClosed() {
        session = null
        _state.update { if (it.connection == Connection.CONNECTED) it.copy(connection = Connection.OFFLINE) else it }
    }

    /** Se reconnecte tant que l'application est visible et la TV joignable. */
    private fun startKeepAlive() {
        if (!foreground || keepAlive?.isActive == true) return
        keepAlive = viewModelScope.launch {
            var wait = 0L
            while (isActive) {
                delay(wait)
                val host = _state.value.host ?: return@launch
                if (session?.isOpen != true) {
                    try {
                        openSession(host)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: PairingRequiredException) {
                        _state.update {
                            it.copy(connection = Connection.NONE, error = "La TV ne reconnaît plus ce téléphone : refaites l'appairage")
                        }
                        return@launch
                    } catch (e: Exception) {
                        _state.update { it.copy(connection = Connection.OFFLINE) }
                    }
                }
                wait = if (session?.isOpen == true) 1_000 else 4_000
            }
        }
    }

    private fun closeSession() {
        session?.close()
        session = null
    }

    private fun closePairing() {
        pairing?.close()
        pairing = null
    }

    // --- Commandes ---

    fun key(code: Int, direction: Int = Keys.SHORT) = command { sendKey(code, direction) }

    fun text(value: String) = command { sendText(value) }

    fun launch(app: TvApp) = command {
        launchApp(app.packageName)
        say("Ouverture de ${app.label}…")
    }

    private fun command(action: RemoteSession.() -> Unit) {
        val s = session
        if (s == null || _state.value.connection != Connection.CONNECTED) {
            say(if (_state.value.connection == Connection.OFFLINE) "TV éteinte : appuyez d'abord sur ⏻" else "TV non connectée")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { s.action() }.onFailure { say("Connexion perdue, reconnexion…") }
        }
    }

    /** Éteint la TV allumée ; allume la TV éteinte (Wake-on-LAN) ou en veille. */
    fun power() {
        val current = _state.value
        val s = session
        viewModelScope.launch(Dispatchers.IO) {
            if (current.connection == Connection.CONNECTED && s != null) {
                runCatching { s.sendKey(Keys.POWER) }
                if (current.tv.isOn) return@launch
            }
            val mac = current.mac
            if (mac != null) {
                runCatching { WakeOnLan.send(mac, current.host) }
                if (current.connection != Connection.CONNECTED) say("Allumage de la TV…")
            } else if (current.connection != Connection.CONNECTED) {
                say("Adresse MAC inconnue : renseignez-la dans les réglages ⚙")
            }
        }
    }

    fun setMac(value: String): Boolean {
        if (value.isBlank()) {
            prefs.edit().remove(KEY_MAC).apply()
            _state.update { it.copy(mac = null) }
            return true
        }
        val mac = WakeOnLan.normalizeMac(value) ?: return false
        prefs.edit().putString(KEY_MAC, mac).apply()
        _state.update { it.copy(mac = mac) }
        return true
    }

    // --- Applications ---

    fun scanApps() {
        val host = _state.value.host ?: return
        if (_state.value.connection != Connection.CONNECTED) {
            say("Allumez d'abord la TV")
            return
        }
        _state.update {
            it.copy(
                scanningApps = true,
                appsMessage = "Si la TV demande « Autoriser le débogage ? », cochez « Toujours autoriser » " +
                    "puis choisissez OK avec les flèches de cette télécommande (1 minute).",
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AppCatalog.scanInstalledApps(host, getApplication<Application>().filesDir) }
            }
            result.onSuccess { packages ->
                catalog.setInstalled(packages)
                _state.update {
                    it.copy(
                        scanningApps = false,
                        apps = catalog.list(),
                        appsComplete = catalog.isComplete,
                        appsMessage = "${catalog.list().size} applications trouvées sur la TV.",
                    )
                }
            }.onFailure {
                _state.update {
                    it.copy(
                        scanningApps = false,
                        appsMessage = "Lecture impossible : activez le « Débogage USB » dans les options pour " +
                            "les développeurs de la TV, puis acceptez la demande d'autorisation.",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        closeSession()
        closePairing()
    }

    private fun clientName(): String = "Télécommande (${Build.MODEL})"

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_MAC = "mac"
    }
}
