package io.github.itlearner0.tvremote.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import io.github.itlearner0.tvremote.protocol.VoiceCommands
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.github.itlearner0.tvremote.quick.QuickRemote
import io.github.itlearner0.tvremote.quick.RemoteNotification
import io.github.itlearner0.tvremote.quick.RemoteWidget
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.itlearner0.tvremote.data.InstalledApp
import io.github.itlearner0.tvremote.protocol.ListedApp
import java.io.File
import android.net.ConnectivityManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.itlearner0.tvremote.data.AppCatalog
import io.github.itlearner0.tvremote.data.FoundTv
import io.github.itlearner0.tvremote.data.LocalBackup
import io.github.itlearner0.tvremote.data.SavedTv
import io.github.itlearner0.tvremote.data.TvAdb
import io.github.itlearner0.tvremote.data.TvApp
import io.github.itlearner0.tvremote.data.TvDiscovery
import io.github.itlearner0.tvremote.data.TvStore
import io.github.itlearner0.tvremote.ir.IrBlaster
import io.github.itlearner0.tvremote.ir.IrCodeset
import io.github.itlearner0.tvremote.ir.IrDatabase
import io.github.itlearner0.tvremote.protocol.ClientIdentity
import io.github.itlearner0.tvremote.protocol.InvalidCodeException
import io.github.itlearner0.tvremote.protocol.Keys
import io.github.itlearner0.tvremote.protocol.MacSources
import io.github.itlearner0.tvremote.protocol.NetworkScanner
import io.github.itlearner0.tvremote.protocol.ScannedTv
import io.github.itlearner0.tvremote.protocol.PairingRequiredException
import io.github.itlearner0.tvremote.protocol.PairingSession
import io.github.itlearner0.tvremote.protocol.RemoteSession
import io.github.itlearner0.tvremote.protocol.TextKeys
import io.github.itlearner0.tvremote.protocol.TvState
import io.github.itlearner0.tvremote.protocol.WakeOnLan
import io.github.itlearner0.tvremote.TvRemoteApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.plus
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.coroutineScope
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
import java.net.Inet4Address

enum class Connection {
    /** Rien en cours (écran de choix de TV si aucune n'est enregistrée). */
    NONE,

    /** Connexion à une nouvelle TV depuis l'écran de choix. */
    CONNECTING,

    /** Code affiché sur la TV, en attente de saisie. */
    PAIRING,

    /** TV enregistrée mais éteinte ou injoignable. */
    OFFLINE,
    CONNECTED,
}

data class UiState(
    val connection: Connection = Connection.NONE,
    val tvs: List<SavedTv> = emptyList(),
    val current: SavedTv? = null,
    /** Écran d'ajout d'une TV affiché alors que d'autres TV sont enregistrées. */
    val adding: Boolean = false,
    /** Adresse de la TV en cours d'ajout ou d'appairage. */
    val pendingHost: String? = null,
    val tv: TvState = TvState(),
    val error: String? = null,
    /** La TV enregistrée ne reconnaît plus ce téléphone. */
    val needsPairing: Boolean = false,
    val discovering: Boolean = false,
    val discovered: List<FoundTv> = emptyList(),
    val apps: List<TvApp> = emptyList(),
    val appsComplete: Boolean = false,
    val scanningApps: Boolean = false,
    val appsMessage: String? = null,
    val detectingMac: Boolean = false,
    /** Le micro du téléphone est en cours d'envoi à la TV. */
    val listening: Boolean = false,
    /** Télécommande affichée dans les notifications. */
    val notificationEnabled: Boolean = false,
    /** Change à chaque nouvelle lecture des applications (pour recharger les icônes). */
    val appsVersion: Int = 0,
    /** Résultat détaillé de la dernière détection d'adresse MAC (affiché dans les réglages). */
    val macStatus: String? = null,
    /** Détail du dernier plantage ou de la dernière erreur rattrapée, à transmettre. */
    val crashReport: String? = null,
    /** Codes choisis pour les touches qui varient selon les fabricants. */
    val customKeys: Map<CustomKey, Int> = emptyMap(),
    /** Assistant d'ajout d'une TV par infrarouge affiché. */
    val irSetup: Boolean = false,
    /** Le téléphone possède un émetteur infrarouge. */
    val irAvailable: Boolean = false,
    /** L'assistant infrarouge associe des codes à la TV Wi-Fi affichée (au lieu d'ajouter une TV). */
    val irAttach: Boolean = false,
    /** Assistant Bluetooth affiché ; [btAttach] : associer le Bluetooth à la TV Wi-Fi affichée. */
    val btSetup: Boolean = false,
    val btAttach: Boolean = false,
    /** État de la télécommande Bluetooth du téléphone. */
    val bt: io.github.itlearner0.tvremote.bt.BtState = io.github.itlearner0.tvremote.bt.BtState(),
) {
    /** La TV affichée est pilotée en Bluetooth seulement. */
    val isBt: Boolean get() = current?.isBt == true

    /** Le téléphone est connecté en Bluetooth à la TV affichée. */
    val btConnected: Boolean get() = current?.btAddress?.let { it == bt.connectedAddress } == true

    /** Les touches partent en Bluetooth (TV Bluetooth, ou TV Wi-Fi injoignable connectée en Bluetooth). */
    val usesBt: Boolean
        get() = isBt || current?.btFallback == true && connection != Connection.CONNECTED

    /** TV sans réseau (infrarouge ou Bluetooth) : pas d'applications, de duplication, etc. */
    val offline: Boolean get() = current?.offline == true

    /** La TV Wi-Fi affichée passe à l'infrarouge quand elle est injoignable. */
    val irFallback: Boolean get() = current?.irFallback == true

    /** Les touches partent par infrarouge (TV infrarouge, ou TV Wi-Fi injoignable avec infrarouge associé). */
    val usesIr: Boolean get() = !usesBt && (isIr || irFallback && connection != Connection.CONNECTED)

    val showSetup: Boolean
        get() = current == null || adding || irSetup || btSetup || connection == Connection.PAIRING || connection == Connection.CONNECTING

    /** La TV affichée est pilotée par infrarouge. */
    val isIr: Boolean get() = current?.isIr == true
}

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("tv", Context.MODE_PRIVATE)
    private val store = TvStore(prefs)
    private val discovery = TvDiscovery(application)
    private val adb = TvAdb(application.filesDir)
    private val ir = IrBlaster(application)
    val irDatabase: IrDatabase by lazy { IrDatabase.load(application) }
    private val identity by lazy { ClientIdentity.loadOrCreate(application.filesDir) }
    private var catalog: AppCatalog? = null

    /**
     * Portée des tâches de fond : une erreur imprévue y est rattrapée et
     * signalée au lieu de fermer l'application.
     */
    private val scope = viewModelScope + CoroutineExceptionHandler { _, error ->
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        runCatching {
            TvRemoteApp.crashFile(application).writeText("Erreur rattrapée (l'application ne s'est pas fermée)\n\n" + trace.take(12_000))
        }
        say("Erreur inattendue (${error.javaClass.simpleName})")
        _state.update { it.copy(crashReport = TvRemoteApp.crashFile(application).takeIf { f -> f.exists() }?.readText()) }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Messages ponctuels (affichés en bas de l'écran). */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    @Volatile private var session: RemoteSession? = null
    private var pairing: PairingSession? = null
    private var pendingName: String? = null
    private var pendingMac: String? = null

    /** Adresses MAC lues par la recherche directe, par adresse IP. */
    private val scannedMacs = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var keepAlive: Job? = null
    private var discoveryJob: Job? = null
    private var foreground = false

    /** Heure du dernier réveil demandé : à la reconnexion, on finit d'allumer l'écran. */
    @Volatile private var wakeRequestedAt = 0L

    /** Sauvegarde locale automatique, deux secondes après chaque modification des réglages. */
    private var backupJob: Job? = null
    private val backupListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> scheduleBackup() }

    init {
        loadCurrentTv()
        val crash = TvRemoteApp.crashFile(application).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }
        _state.update {
            it.copy(
                irAvailable = ir.available,
                crashReport = crash,
                notificationEnabled = RemoteNotification.isEnabled(application),
            )
        }
        prefs.registerOnSharedPreferenceChangeListener(backupListener)
        scope.launch {
            io.github.itlearner0.tvremote.bt.BtRemote.state.collect { bt -> _state.update { it.copy(bt = bt) } }
        }
    }

    private fun scheduleBackup() {
        backupJob?.cancel()
        backupJob = scope.launch(Dispatchers.IO) {
            delay(2_000)
            // Pas de sauvegarde vide : elle masquerait une bonne sauvegarde après réinstallation.
            if (store.all().isEmpty()) return@launch
            runCatching { LocalBackup.writeAutomatic(getApplication<Application>(), LocalBackup.export(prefs)) }
        }
    }

    /** Exporte les réglages dans le fichier choisi par l'utilisateur. */
    fun exportBackup(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            runCatching { LocalBackup.writeTo(getApplication<Application>(), uri, LocalBackup.export(prefs)) }
                .onSuccess { say("Sauvegarde enregistrée") }
                .onFailure { say("Échec de la sauvegarde : ${it.message}") }
        }
    }

    /** Restaure les réglages depuis une sauvegarde choisie par l'utilisateur. */
    fun importBackup(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { LocalBackup.import(prefs, LocalBackup.readFrom(getApplication<Application>(), uri)) }
            }
            result.onSuccess { count ->
                stopKeepAlive()
                closeSession()
                closePairing()
                _state.update { it.copy(adding = false, irSetup = false, error = null) }
                loadCurrentTv()
                startKeepAlive()
                say("$count TV restaurée(s). Si la TV le demande, refaites l'appairage.")
            }.onFailure { say("Restauration impossible : ${it.message}") }
        }
    }

    fun dismissCrashReport() {
        TvRemoteApp.crashFile(getApplication()).delete()
        _state.update { it.copy(crashReport = null) }
    }

    fun consumeMessage() {
        _messages.value = null
    }

    private fun say(message: String) {
        _messages.value = message
    }

    private fun loadCurrentTv() {
        val current = store.current()
        catalog = current?.let { AppCatalog(prefs, it.id) }
        // Le widget et la notification affichent le nom de la TV choisie.
        runCatching {
            RemoteWidget.refresh(getApplication())
            RemoteNotification.refresh(getApplication())
        }
        // TV Bluetooth : connexion d'office.
        current?.takeIf { it.isBt }?.btAddress?.let { address ->
            runCatching { io.github.itlearner0.tvremote.bt.BtRemote.init(getApplication(), address) }
        }
        _state.update {
            it.copy(
                tvs = store.all(),
                current = current,
                connection = when {
                    current == null -> Connection.NONE
                    current.offline -> Connection.CONNECTED // l'infrarouge et le Bluetooth n'ont pas besoin du réseau
                    else -> Connection.OFFLINE
                },
                tv = TvState(),
                needsPairing = false,
                apps = catalog?.list().orEmpty(),
                appsComplete = catalog?.isComplete ?: false,
                appsMessage = null,
                customKeys = loadCustomKeys(current?.id),
            )
        }
    }

    private fun loadCustomKeys(tvId: String?): Map<CustomKey, Int> {
        if (tvId == null) return emptyMap()
        return CustomKey.entries.associateWith { prefs.getInt("key_${tvId}_${it.name}", it.default) }
    }

    /** Envoie la commande choisie pour une touche personnalisable. */
    fun customKey(custom: CustomKey) = key(_state.value.customKeys[custom] ?: custom.default)

    fun setCustomKey(key: CustomKey, code: Int) {
        val tv = _state.value.current ?: return
        prefs.edit().putInt("key_${tv.id}_${key.name}", code).apply()
        _state.update { it.copy(customKeys = it.customKeys + (key to code)) }
    }

    private fun updateTv(change: SavedTv.() -> SavedTv) {
        val current = _state.value.current ?: return
        val updated = current.change()
        store.save(updated)
        _state.update { it.copy(current = updated, tvs = store.all()) }
    }

    // --- Cycle de vie : connexion seulement quand l'application est visible ---

    fun onForeground() {
        foreground = true
        if (!_state.value.showSetup) startKeepAlive()
    }

    fun onBackground() {
        foreground = false
        stopKeepAlive()
        closeSession()
        _state.update { if (it.connection == Connection.CONNECTED) it.copy(connection = Connection.OFFLINE) else it }
    }

    // --- Plusieurs TV ---

    fun selectTv(id: String) {
        if (id == _state.value.current?.id) return
        stopKeepAlive()
        closeSession()
        store.select(id)
        loadCurrentTv()
        startKeepAlive()
    }

    /** Ouvre l'écran d'ajout d'une TV (les TV enregistrées sont conservées). */
    fun addTv() {
        stopKeepAlive()
        closeSession()
        _state.update { it.copy(adding = true, connection = Connection.NONE, error = null, discovered = emptyList()) }
    }

    fun cancelSetup() {
        closePairing()
        _state.update { it.copy(adding = false, error = null, pendingHost = null) }
        loadCurrentTv()
        startKeepAlive()
    }

    fun removeCurrentTv() {
        val current = _state.value.current ?: return
        stopKeepAlive()
        closeSession()
        AppCatalog(prefs, current.id).clear()
        store.remove(current.id)
        loadCurrentTv()
        startKeepAlive()
    }

    /** Modifie le nom, l'adresse IP et l'adresse MAC de la TV. Renvoie un message d'erreur ou null. */
    fun editCurrentTv(name: String, host: String, mac: String): String? {
        val current = _state.value.current ?: return null
        val normalizedMac = if (mac.isBlank()) null else WakeOnLan.normalizeMac(mac) ?: return "Adresse MAC invalide"
        val newHost = host.trim()
        if (newHost.isEmpty() && !current.offline) return "Adresse IP manquante"
        updateTv { copy(name = name.trim().ifEmpty { current.name }, host = newHost, mac = normalizedMac) }
        if (newHost != current.host) {
            stopKeepAlive()
            closeSession()
            _state.update { it.copy(connection = Connection.OFFLINE) }
            startKeepAlive()
        }
        return null
    }

    // --- Choix d'une nouvelle TV et appairage ---

    /** Recherche les TV en continu tant que l'écran d'ajout est affiché. */
    fun startAutoDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = scope.launch {
            while (isActive) {
                // Pendant l'appairage, ne pas solliciter la TV (son port d'appairage est occupé).
                if (_state.value.connection == Connection.PAIRING || _state.value.connection == Connection.CONNECTING) {
                    delay(1_000)
                    continue
                }
                _state.update { it.copy(discovering = true) }
                coroutineScope {
                    // Annonce réseau (mDNS) et recherche directe en parallèle : la plus rapide l'emporte.
                    launch { withTimeoutOrNull(6_000) { discovery.discover().collect { addDiscovered(it) } } }
                    launch {
                        for (tv in scanNetwork()) addDiscovered(FoundTv(tv.name ?: "Android TV", tv.host))
                    }
                }
                _state.update { it.copy(discovering = false) }
                delay(2_000)
            }
        }
    }

    private fun addDiscovered(tv: FoundTv) {
        _state.update { s ->
            val known = s.discovered.firstOrNull { it.host == tv.host }
            when {
                known == null -> s.copy(discovered = s.discovered + tv)
                // Le nom annoncé (mDNS) est plus parlant que le nom générique de la recherche directe.
                known.name == "Android TV" && tv.name != known.name ->
                    s.copy(discovered = s.discovered.map { if (it.host == tv.host) tv else it })
                else -> s
            }
        }
    }

    /** Recherche directe : port de télécommande testé sur tout le réseau local du téléphone. */
    private suspend fun scanNetwork(): List<ScannedTv> = withContext(Dispatchers.IO) {
        val tvs = runCatching { NetworkScanner.scan(phoneIpv4Addresses(), identity) }.getOrDefault(emptyList())
        for (tv in tvs) tv.mac?.let { scannedMacs[tv.host] = it }
        tvs
    }

    fun stopAutoDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _state.update { it.copy(discovering = false) }
    }

    /** Réveille une TV pas encore ajoutée (éteinte, donc invisible) grâce à son adresse MAC. */
    fun wakeByMac(value: String) {
        val mac = WakeOnLan.normalizeMac(value)
        if (mac == null) {
            say("Adresse MAC invalide (exemple : A4:B1:C2:D3:E4:F5)")
            return
        }
        pendingMac = mac
        wakeRequestedAt = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            runCatching { WakeOnLan.send(mac, null, phoneBroadcastAddresses()) }
        }
        say("Signal de réveil envoyé : la TV devrait apparaître dans la liste d'ici 30 secondes.")
    }

    fun connect(host: String, name: String? = null) {
        val target = host.trim()
        if (target.isEmpty()) {
            _state.update { it.copy(error = "Saisissez l'adresse IP de la TV") }
            return
        }
        stopKeepAlive()
        closeSession()
        closePairing()
        pendingName = name
        _state.update { it.copy(connection = Connection.CONNECTING, pendingHost = target, error = null) }
        scope.launch {
            try {
                val s = openSession(target)
                registerTv(target, s)
            } catch (e: PairingRequiredException) {
                startPairing(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(connection = Connection.NONE, error = "Impossible de joindre la TV : vérifiez qu'elle est allumée, l'adresse et le Wi-Fi")
                }
            }
        }
    }

    /** Refait l'appairage de la TV enregistrée (quand elle ne reconnaît plus ce téléphone). */
    fun repairCurrentTv() {
        val current = _state.value.current ?: return
        stopKeepAlive()
        closeSession()
        pendingName = current.name
        _state.update { it.copy(connection = Connection.CONNECTING, pendingHost = current.host, error = null) }
        scope.launch { startPairing(current.host) }
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
            _state.update { it.copy(connection = Connection.PAIRING, pendingHost = host) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(connection = Connection.NONE, error = "La TV n'a pas lancé l'appairage. Réessayez.") }
        }
    }

    fun submitCode(code: String) {
        val p = pairing ?: return
        val host = _state.value.pendingHost ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { p.finish(code) }
                closePairing()
                _state.update { it.copy(connection = Connection.CONNECTING, error = null) }
                registerTv(host, openSession(host))
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

    /** Enregistre (ou met à jour) la TV qui vient de se connecter et la sélectionne. */
    private fun registerTv(host: String, s: RemoteSession) {
        val existing = store.findByHost(host) ?: _state.value.current?.takeIf { it.host == host }
        val model = listOfNotNull(s.state.device?.manufacturer, s.state.device?.model)
            .filter { it.isNotBlank() }.joinToString(" ")
        val tv = SavedTv(
            id = existing?.id ?: store.newId(),
            name = existing?.name ?: pendingName ?: model.ifBlank { "TV" },
            host = host,
            mac = existing?.mac ?: s.macAddress ?: scannedMacs[host] ?: pendingMac,
            serviceName = existing?.serviceName ?: pendingName,
            irCodeset = existing?.irCodeset,
            control = existing?.control ?: SavedTv.CONTROL_AUTO,
        )
        pendingMac = null
        store.save(tv)
        store.select(tv.id)
        catalog = AppCatalog(prefs, tv.id)
        _state.update {
            it.copy(
                tvs = store.all(),
                current = tv,
                adding = false,
                pendingHost = null,
                needsPairing = false,
                connection = Connection.CONNECTED,
                tv = s.state,
                apps = catalog!!.list(),
                appsComplete = catalog!!.isComplete,
                customKeys = loadCustomKeys(tv.id),
            )
        }
        onTvState(s.state)
        afterConnected(tv)
        startKeepAlive()
    }

    // --- Connexion permanente ---

    private suspend fun openSession(host: String): RemoteSession {
        val s = withContext(Dispatchers.IO) {
            RemoteSession(
                host = host,
                identity = identity,
                onState = ::onTvState,
                onClosed = { onSessionClosed() },
            ).also { it.connect() }
        }
        session = s
        QuickRemote.appSession = s
        return s
    }

    private fun onTvState(tv: TvState) {
        val c = catalog
        if (c != null && tv.currentApp.isNotEmpty() && c.remember(tv.currentApp)) {
            _state.update { it.copy(apps = c.list()) }
        }
        _state.update { if (it.connection == Connection.CONNECTED) it.copy(tv = tv) else it }
    }

    private fun onSessionClosed() {
        session = null
        QuickRemote.appSession = null
        _state.update { if (it.connection == Connection.CONNECTED) it.copy(connection = Connection.OFFLINE) else it }
    }

    /** Une fois connecté : compléter l'adresse MAC, le nom réseau, et finir un réveil en cours. */
    private fun afterConnected(tv: SavedTv) {
        val s = session ?: return
        scope.launch(Dispatchers.IO) {
            if (System.currentTimeMillis() - wakeRequestedAt < WAKE_WINDOW_MS && !s.state.isOn) {
                runCatching { s.sendKey(Keys.WAKEUP) }
            }
            wakeRequestedAt = 0
            if (tv.mac == null) {
                val mac = findMac(tv, s, useAdb = adbAuthorized(tv), log = mutableListOf())
                if (mac != null) updateTvIfCurrent(tv.id) { copy(mac = mac) }
            }
            if (tv.serviceName == null) {
                val found = discoverFor(5_000).firstOrNull { it.host == tv.host }
                found?.let { f -> updateTvIfCurrent(tv.id) { copy(serviceName = f.name) } }
            }
        }
    }

    private fun updateTvIfCurrent(id: String, change: SavedTv.() -> SavedTv) {
        val saved = store.all().firstOrNull { it.id == id } ?: return
        val updated = saved.change()
        store.save(updated)
        _state.update { s -> s.copy(tvs = store.all(), current = if (s.current?.id == id) updated else s.current) }
    }

    /** Se reconnecte tant que l'application est visible ; retrouve la TV si son adresse IP a changé. */
    private fun startKeepAlive() {
        if (!foreground || _state.value.showSetup || _state.value.offline || keepAlive?.isActive == true) return
        keepAlive = scope.launch {
            var failures = 0
            while (isActive) {
                val tv = _state.value.current ?: return@launch
                if (session?.isOpen != true) {
                    try {
                        openSession(tv.host)
                        failures = 0
                        _state.update { it.copy(connection = Connection.CONNECTED, needsPairing = false, tv = session?.state ?: TvState()) }
                        session?.let { onTvState(it.state) }
                        afterConnected(tv)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: PairingRequiredException) {
                        failures++
                        // Peut aussi arriver pendant que la TV démarre : on réessaie, et on propose de ré-appairer.
                        _state.update { it.copy(connection = Connection.OFFLINE, needsPairing = failures >= 2) }
                    } catch (e: Exception) {
                        failures++
                        _state.update { it.copy(connection = Connection.OFFLINE) }
                        if (failures % 6 == 3) relocate(tv)
                    }
                }
                val waking = System.currentTimeMillis() - wakeRequestedAt < WAKE_WINDOW_MS
                delay(
                    when {
                        session?.isOpen == true -> 1_000
                        waking -> 1_000
                        else -> 3_000
                    },
                )
            }
        }
    }

    /** La TV ne répond plus à son adresse : la chercher sur le réseau par son nom. */
    private suspend fun relocate(tv: SavedTv) {
        tv.serviceName?.let { name ->
            discoverFor(4_000).firstOrNull { it.name == name && it.host != tv.host }?.let { found ->
                updateTvIfCurrent(tv.id) { copy(host = found.host) }
                return
            }
        }
        // Recherche directe : on reconnaît la TV à son adresse MAC.
        val mac = tv.mac ?: return
        scanNetwork().firstOrNull { it.mac == mac && it.host != tv.host }?.let { found ->
            updateTvIfCurrent(tv.id) { copy(host = found.host) }
        }
    }

    /** Recherche les TV du réseau pendant [ms] millisecondes et renvoie celles trouvées. */
    private suspend fun discoverFor(ms: Long): List<FoundTv> {
        val found = mutableListOf<FoundTv>()
        withTimeoutOrNull(ms) { discovery.discover().collect { found += it } }
        return found
    }

    private fun stopKeepAlive() {
        keepAlive?.cancel()
        keepAlive = null
    }

    private fun closeSession() {
        session?.close()
        session = null
        QuickRemote.appSession = null
    }

    // --- Accès rapide (notification) ---

    fun setNotificationEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        RemoteNotification.setEnabled(app, enabled)
        _state.update { it.copy(notificationEnabled = enabled) }
        if (enabled && !RemoteNotification.canShow(app)) {
            say("Les notifications de l'application sont bloquées : autorisez-les dans la page qui s'ouvre")
            runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        } else if (enabled) {
            say("Télécommande ajoutée aux notifications : déroulez le volet des notifications")
        }
    }

    // --- Recherche vocale ---

    private var voiceJob: Job? = null

    /**
     * Démarre ou arrête l'écoute : le son du micro est envoyé à l'Assistant de
     * la TV (16 bits mono 8 000 Hz). Arrêt automatique au bout de 12 secondes.
     */
    @SuppressLint("MissingPermission") // l'écran demande l'autorisation avant d'appeler
    fun toggleVoice() {
        if (voiceJob?.isActive == true) {
            stopVoice()
            return
        }
        val s = session
        if (s == null || !s.isOpen || _state.value.connection != Connection.CONNECTED) {
            say("TV éteinte : appuyez d'abord sur Marche")
            return
        }
        voiceJob = scope.launch(Dispatchers.IO) {
            val id = s.startVoice()
            if (id == null) {
                say("La TV n'a pas ouvert la recherche vocale")
                return@launch
            }
            val rate = RemoteSession.VOICE_SAMPLE_RATE
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, 16 * 1024),
            )
            _state.update { it.copy(listening = true) }
            try {
                recorder.startRecording()
                val chunk = ByteArray(8 * 1024) // une demi-seconde de son
                val deadline = System.currentTimeMillis() + 12_000
                while (isActive && System.currentTimeMillis() < deadline) {
                    var filled = 0
                    while (filled < chunk.size && isActive) {
                        val n = recorder.read(chunk, filled, chunk.size - filled)
                        if (n <= 0) break
                        filled += n
                    }
                    if (filled > 0) s.sendVoice(id, chunk.copyOf(filled))
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                runCatching { s.endVoice(id) }
                _state.update { it.copy(listening = false) }
            }
        }
    }

    fun stopVoice() {
        voiceJob?.cancel()
        voiceJob = null
    }

    private fun closePairing() {
        pairing?.close()
        pairing = null
    }

    // --- Commandes ---

    fun key(code: Int, direction: Int = Keys.SHORT) {
        if (_state.value.usesBt) {
            btKey(code)
            return
        }
        // Wi-Fi revenu : on libère le Bluetooth (la TV masque son clavier à l'écran tant
        // qu'un clavier Bluetooth est connecté).
        if (_state.value.current?.btFallback == true && _state.value.btConnected) {
            io.github.itlearner0.tvremote.bt.BtRemote.disconnect()
        }
        if (_state.value.usesIr) {
            irKey(code)
            return
        }
        command { sendKey(code, direction) }
    }

    /**
     * Envoie du texte dans le champ actif de la TV. Beaucoup d'applications
     * ignorent le canal de saisie de la télécommande : on tape donc le texte
     * comme sur un clavier (ADB si autorisé, sinon touche par touche).
     */
    fun text(value: String) {
        if (_state.value.usesBt) {
            if (!_state.value.btConnected) return say("Bluetooth : TV non connectée")
            val ascii = TextKeys.toAscii(value)
            val missing = io.github.itlearner0.tvremote.bt.BtRemote.type(ascii)
            if (ascii != value || missing.isNotEmpty()) say("Les accents et certains signes ne peuvent pas être tapés en Bluetooth")
            return
        }
        if (notOverIr("La saisie de texte")) return
        val tv = _state.value.current ?: return
        val s = session
        if (s == null || !s.isOpen || _state.value.connection != Connection.CONNECTED) {
            say("TV éteinte : appuyez d'abord sur Marche")
            return
        }
        scope.launch(Dispatchers.IO) {
            val ascii = TextKeys.toAscii(value)
            val (strokes, unsupported) = TextKeys.strokes(ascii)
            if (ascii != value || unsupported.isNotEmpty()) {
                say(
                    "Les accents et certains signes ne peuvent pas être tapés à distance" +
                        if (unsupported.isNotEmpty()) " (${unsupported.joinToString(" ")} ignorés)" else "",
                )
            }
            val typedByAdb = adbAuthorized(tv) && unsupported.isEmpty() &&
                runCatching { adb.inputText(tv.host, ascii) }.isSuccess
            if (typedByAdb) return@launch
            runCatching {
                for (stroke in strokes) {
                    if (stroke.shift) s.sendKey(TextKeys.SHIFT_LEFT, Keys.START_LONG)
                    s.sendKey(stroke.keyCode)
                    if (stroke.shift) s.sendKey(TextKeys.SHIFT_LEFT, Keys.END_LONG)
                    delay(40)
                }
            }.onFailure { say("Connexion perdue pendant la saisie") }
        }
    }

    /** Les fonctions réseau n'existent pas en infrarouge : message et true si la TV est en infrarouge. */
    private fun notOverIr(feature: String): Boolean {
        if (_state.value.isBt) {
            say("$feature n'est pas disponible avec une TV pilotée en Bluetooth")
            return true
        }
        if (!_state.value.isIr) return false
        say("$feature n'est pas disponible avec une TV pilotée par infrarouge")
        return true
    }

    // --- Bluetooth ---

    private fun btKey(keyCode: Int) {
        if (!io.github.itlearner0.tvremote.pro.Pro.require(getApplication(), io.github.itlearner0.tvremote.pro.ProFeature.BLUETOOTH)) return
        val bt = io.github.itlearner0.tvremote.bt.BtRemote
        if (!_state.value.btConnected) {
            _state.value.current?.btAddress?.let { bt.init(getApplication(), it) }
            say("Bluetooth : connexion à la TV… réessayez dans un instant")
            return
        }
        if (!bt.key(keyCode)) say("Cette touche n'existe pas en Bluetooth")
    }

    /** Ouvre l'assistant Bluetooth ; [attach] : associer le Bluetooth à la TV Wi-Fi affichée. */
    fun startBtSetup(attach: Boolean = false) {
        if (!io.github.itlearner0.tvremote.pro.Pro.require(getApplication(), io.github.itlearner0.tvremote.pro.ProFeature.BLUETOOTH)) return
        if (!attach) {
            stopKeepAlive()
            closeSession()
        }
        _state.update { it.copy(btSetup = true, btAttach = attach, error = null) }
    }

    fun cancelBtSetup() {
        _state.update { it.copy(btSetup = false, btAttach = false) }
        if (_state.value.current != null && !_state.value.adding) startKeepAlive()
    }

    /** Enregistre une TV Bluetooth, ou associe le Bluetooth à la TV Wi-Fi affichée. */
    fun addBtTv(address: String, name: String?) {
        if (_state.value.btAttach && _state.value.current != null) {
            updateTv { copy(btAddress = address) }
            _state.update { it.copy(btSetup = false, btAttach = false, error = null) }
            startKeepAlive()
            say("Bluetooth associé : la télécommande l'utilisera si la TV ne répond pas par le Wi-Fi.")
            return
        }
        val tv = SavedTv(
            id = store.newId(),
            name = (name?.takeIf { it.isNotBlank() } ?: "TV") + " (Bluetooth)",
            host = "",
            btAddress = address,
        )
        store.save(tv)
        store.select(tv.id)
        _state.update { it.copy(btSetup = false, adding = false, error = null) }
        loadCurrentTv()
        say("${tv.name} ajoutée.")
    }

    fun removeBt() {
        io.github.itlearner0.tvremote.bt.BtRemote.disconnect()
        updateTv { copy(btAddress = null, control = if (control == SavedTv.CONTROL_BT) SavedTv.CONTROL_AUTO else control) }
        reloadAfterControlChange()
    }

    // --- Infrarouge ---

    /** Émet la touche [keyCode] avec le jeu de codes de la TV infrarouge affichée. */
    private fun irKey(keyCode: Int) {
        val codeset = irDatabase.codeset(_state.value.current?.irCodeset)
        if (codeset == null) {
            say("Jeu de codes infrarouges introuvable : supprimez puis ajoutez de nouveau la TV")
            return
        }
        val name = IrDatabase.irKeysFor(keyCode).firstOrNull { it in codeset.keys }
        if (name == null) {
            say("Cette touche n'existe pas pour ce modèle de TV")
            return
        }
        sendIr(codeset, name)
    }

    /** Émet une touche d'un jeu de codes (aussi utilisé pour les essais de l'assistant). */
    fun sendIr(codeset: IrCodeset, keyName: String) {
        val code = codeset.keys[keyName] ?: return
        scope.launch(Dispatchers.IO) {
            val sent = runCatching { ir.send(code) }.getOrDefault(false)
            if (!sent) say("Ce téléphone n'a pas d'émetteur infrarouge")
        }
    }

    /** Ouvre l'assistant infrarouge ; [attach] : associer l'infrarouge à la TV Wi-Fi affichée. */
    fun startIrSetup(attach: Boolean = false) {
        if (!attach) {
            stopKeepAlive()
            closeSession()
        }
        _state.update { it.copy(irSetup = true, irAttach = attach, error = null) }
    }

    fun cancelIrSetup() {
        _state.update { it.copy(irSetup = false, irAttach = false) }
        if (_state.value.current != null && !_state.value.adding) startKeepAlive()
    }

    /** Marque à proposer d'emblée dans l'assistant (celle de la TV Wi-Fi affichée). */
    fun suggestedIrBrand(): String? {
        if (!_state.value.irAttach) return null
        val hints = listOfNotNull(_state.value.current?.name, _state.value.tv.device?.manufacturer).joinToString(" ")
        return irDatabase.brands.firstOrNull { hints.contains(it, ignoreCase = true) }
    }

    fun removeIr() {
        updateTv { copy(irCodeset = null, control = SavedTv.CONTROL_AUTO) }
        reloadAfterControlChange()
    }

    /** Choisit le mode de commande d'une TV Wi-Fi ayant aussi l'infrarouge. */
    fun setControl(control: String) {
        updateTv { copy(control = control) }
        reloadAfterControlChange()
    }

    private fun reloadAfterControlChange() {
        stopKeepAlive()
        closeSession()
        loadCurrentTv()
        startKeepAlive()
    }

    /** Enregistre une TV infrarouge, ou associe les codes à la TV Wi-Fi affichée. */
    fun addIrTv(codeset: IrCodeset) {
        if (_state.value.irAttach && _state.value.current != null) {
            updateTv { copy(irCodeset = codeset.id) }
            _state.update { it.copy(irSetup = false, irAttach = false, error = null) }
            startKeepAlive()
            say("Infrarouge associé : la télécommande l'utilisera si la TV ne répond pas par le Wi-Fi.")
            return
        }
        val tv = SavedTv(id = store.newId(), name = "${codeset.brand} (infrarouge)", host = "", irCodeset = codeset.id)
        store.save(tv)
        store.select(tv.id)
        _state.update { it.copy(irSetup = false, adding = false, error = null) }
        loadCurrentTv()
        say("${tv.name} ajoutée. Visez la TV avec le haut du téléphone.")
    }

    /**
     * Ouvre une application : par le débogage (ADB) s'il est autorisé, ce qui
     * marche pour toutes les applications ; sinon par la télécommande, dont la
     * commande d'ouverture est ignorée par certaines TV (dont Sharp).
     */
    fun launch(app: TvApp) {
        if (notOverIr("L'ouverture d'applications")) return
        val tv = _state.value.current ?: return
        val s = session
        if (_state.value.connection != Connection.CONNECTED) {
            say("TV éteinte : appuyez d'abord sur Marche")
            return
        }
        say("Ouverture de ${app.label}…")
        scope.launch(Dispatchers.IO) {
            val installed = app.installed ?: InstalledApp(app.packageName, "", tvApp = true)
            if (adbAuthorized(tv) || !installed.tvApp) {
                val opened = runCatching { adb.launch(tv.host, installed) }.getOrDefault(false)
                if (opened) {
                    setAdbAuthorized(tv)
                    return@launch
                }
                if (!installed.tvApp) {
                    say("Impossible d'ouvrir ${app.label} : activez le « Débogage USB » sur la TV")
                    return@launch
                }
            }
            runCatching { s?.launchApp(app.packageName) }
                .onFailure { say("Connexion perdue, reconnexion…") }
        }
    }

    /**
     * Phrase dictée (reconnaissance vocale du téléphone) : « lance Netflix »
     * ouvre l'application ; sinon recherche sur la TV (ADB), ou saisie du texte.
     */
    fun voiceQuery(phrase: String) {
        val spoken = phrase.trim()
        if (spoken.isEmpty()) return
        val tv = _state.value.current ?: return
        VoiceCommands.appRequest(spoken)?.let { request ->
            val label = VoiceCommands.bestMatch(request, _state.value.apps.map { it.label })
            val app = _state.value.apps.firstOrNull { it.label == label }
            if (app != null) {
                launch(app)
                return
            }
        }
        if (tv.offline || _state.value.connection != Connection.CONNECTED) {
            say("« $spoken » : TV non connectée")
            return
        }
        scope.launch(Dispatchers.IO) {
            val searched = adbAuthorized(tv) && runCatching { adb.globalSearch(tv.host, spoken) }.getOrDefault(false)
            if (searched) say("Recherche de « $spoken » sur la TV") else text(spoken)
        }
    }

    private fun command(action: RemoteSession.() -> Unit) {
        val s = session
        if (s == null || _state.value.connection != Connection.CONNECTED) {
            say(if (_state.value.connection == Connection.OFFLINE) "TV éteinte : appuyez d'abord sur ⏻" else "TV non connectée")
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching { s.action() }.onFailure { say("Connexion perdue, reconnexion…") }
        }
    }

    /**
     * Marche / arrêt. Les TV ne réagissent pas toutes à la même commande :
     * on essaie successivement Power, Veille (ou Réveil), puis ADB, en
     * vérifiant l'état annoncé par la TV après chaque essai.
     */
    fun power() {
        val tv = _state.value.current ?: return
        if (_state.value.usesBt) {
            btKey(Keys.POWER)
            return
        }
        if (tv.isIr) {
            irKey(Keys.POWER)
            return
        }
        val s = session
        val reachable = s != null && s.isOpen && _state.value.connection == Connection.CONNECTED
        if (!reachable && tv.irFallback) {
            // TV injoignable par le Wi-Fi : l'infrarouge l'allume même complètement éteinte.
            irKey(Keys.WAKEUP)
            wakeRequestedAt = System.currentTimeMillis()
            tv.mac?.let { mac ->
                scope.launch(Dispatchers.IO) { runCatching { WakeOnLan.send(mac, tv.host, phoneBroadcastAddresses()) } }
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            if (s != null && s.isOpen && _state.value.connection == Connection.CONNECTED) {
                val turnOn = !s.state.isOn
                val attempts = if (turnOn) listOf(Keys.WAKEUP, Keys.POWER) else listOf(Keys.POWER, Keys.SLEEP, Keys.TV_POWER)
                for (keyCode in attempts) {
                    runCatching { s.sendKey(keyCode) }
                    if (waitForPower(s, turnOn)) return@launch
                }
                if (adbAuthorized(tv)) {
                    runCatching { adb.keyEvent(tv.host, if (turnOn) Keys.WAKEUP else Keys.SLEEP) }
                    if (waitForPower(s, turnOn)) return@launch
                }
                say(
                    if (turnOn) "La TV n'a pas réagi à la commande d'allumage."
                    else "La TV n'a pas réagi à la commande d'arrêt. Activez le « Débogage USB » sur la TV " +
                        "et listez les applications une fois : la télécommande pourra alors l'éteindre.",
                )
                return@launch
            }
            // TV injoignable : réveil par le réseau.
            val mac = tv.mac
            if (mac == null) {
                say("Adresse MAC de la TV inconnue : allumez la TV à la main une fois, ou saisissez-la dans ⚙")
                return@launch
            }
            wakeRequestedAt = System.currentTimeMillis()
            say("Réveil de la TV par le réseau…")
            runCatching { WakeOnLan.send(mac, tv.host, phoneBroadcastAddresses()) }
            delay(WAKE_WINDOW_MS)
            if (_state.value.connection != Connection.CONNECTED && _state.value.current?.id == tv.id) {
                say("La TV ne répond pas au réveil : activez sur la TV la veille réseau / « Wake on LAN » (voir ⚙)")
            }
        }
    }

    /** Met la TV en veille (touche Veille, puis ADB si elle ne réagit pas). */
    fun sleep() {
        val tv = _state.value.current ?: return
        if (_state.value.usesBt) {
            btKey(Keys.SLEEP)
            return
        }
        if (tv.isIr) {
            irKey(Keys.SLEEP)
            return
        }
        val s = session
        if ((s == null || !s.isOpen || _state.value.connection != Connection.CONNECTED) && tv.irFallback) {
            irKey(Keys.SLEEP)
            return
        }
        if (s == null || !s.isOpen || _state.value.connection != Connection.CONNECTED) {
            say("La TV est déjà éteinte")
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching { s.sendKey(Keys.SLEEP) }
            if (waitForPower(s, on = false)) return@launch
            if (adbAuthorized(tv)) {
                runCatching { adb.keyEvent(tv.host, Keys.SLEEP) }
                if (waitForPower(s, on = false)) return@launch
            }
            runCatching { s.sendKey(Keys.POWER) }
            if (!waitForPower(s, on = false)) say("La TV n'a pas réagi à la mise en veille.")
        }
    }

    /** Attend que la TV annonce l'état voulu (ou se déconnecte, si on l'éteint). */
    private suspend fun waitForPower(s: RemoteSession, on: Boolean): Boolean {
        repeat(12) {
            delay(250)
            if (!s.isOpen) return !on
            if (s.state.isOn == on) return true
        }
        return false
    }

    /** Adresses de diffusion du réseau du téléphone, pour le réveil par le réseau. */
    /** Adresses IPv4 du téléphone sur son réseau actif. */
    private fun phoneIpv4Addresses(): List<String> {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val links = cm.getLinkProperties(cm.activeNetwork) ?: return emptyList()
        return links.linkAddresses.mapNotNull { (it.address as? Inet4Address)?.hostAddress }
    }

    private fun phoneBroadcastAddresses(): List<String> {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val links = cm.getLinkProperties(cm.activeNetwork) ?: return emptyList()
        return links.linkAddresses.mapNotNull { link ->
            val address = link.address as? Inet4Address ?: return@mapNotNull null
            val prefix = link.prefixLength
            if (prefix !in 1..30) return@mapNotNull null
            val ip = address.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
            val broadcast = ip or ((1 shl (32 - prefix)) - 1)
            (0..3).joinToString(".") { i -> ((broadcast ushr (24 - 8 * i)) and 0xFF).toString() }
        }
    }

    /** Lit l'adresse MAC par ADB et l'enregistre. */
    fun detectMac() {
        if (notOverIr("La détection de l'adresse MAC")) return
        val tv = _state.value.current ?: return
        if (_state.value.connection != Connection.CONNECTED) {
            say("Allumez d'abord la TV")
            return
        }
        _state.update {
            it.copy(
                detectingMac = true,
                macStatus = "Recherche… Si la TV demande « Autoriser le débogage ? », acceptez avec les flèches.",
            )
        }
        scope.launch {
            val log = mutableListOf<String>()
            val mac = withContext(Dispatchers.IO) { findMac(tv, session, useAdb = true, log = log) }
            if (mac != null) {
                updateTvIfCurrent(tv.id) { copy(mac = mac) }
                _state.update { it.copy(detectingMac = false, macStatus = "✓ Adresse MAC trouvée et enregistrée : $mac") }
            } else {
                _state.update {
                    it.copy(
                        detectingMac = false,
                        macStatus = "Adresse MAC introuvable automatiquement. Saisissez-la ci-dessus : sur la TV, " +
                            "Paramètres → Réseau et Internet → votre réseau, ou Paramètres → À propos → État.\n\n" +
                            "Détail : " + log.joinToString(" · "),
                    )
                }
            }
        }
    }

    /**
     * Cherche l'adresse MAC de la TV : certificat, Google Cast, puis ADB.
     * [log] reçoit le résultat de chaque méthode.
     */
    private fun findMac(tv: SavedTv, s: RemoteSession?, useAdb: Boolean, log: MutableList<String>): String? {
        s?.macAddress?.let { return it }
        log += "certificat (${s?.serverSubject ?: "non lu"}) → rien"
        scannedMacs[tv.host]?.let { return it }
        MacSources.fromCast(tv.host)?.let { return it }
        log += "Google Cast → rien"
        if (!useAdb) return null
        return runCatching { adb.macAddress(tv.host, log) }
            .onSuccess { setAdbAuthorized(tv) }
            .onFailure { log += "débogage (ADB) → ${it.javaClass.simpleName} : non autorisé ou désactivé" }
            .getOrNull()
    }

    private fun adbAuthorized(tv: SavedTv): Boolean = prefs.getBoolean("adb_ok_${tv.id}", false)

    private fun setAdbAuthorized(tv: SavedTv) {
        prefs.edit().putBoolean("adb_ok_${tv.id}", true).apply()
    }

    // --- Applications ---

    /**
     * Relit la liste en arrière-plan si elle date de plus de 5 minutes et que
     * le débogage est déjà autorisé (appelé à l'ouverture de l'onglet Applications).
     */
    fun refreshAppsIfStale() {
        val tv = _state.value.current ?: return
        val c = catalog ?: return
        if (tv.offline || _state.value.scanningApps || _state.value.connection != Connection.CONNECTED) return
        if (!adbAuthorized(tv) || System.currentTimeMillis() - c.lastScan < APPS_REFRESH_MS) return
        scanApps(silent = true)
    }

    /**
     * Liste des applications : d'abord par l'application d'aide (liste exacte,
     * vrais noms, icônes), sinon par la liste des paquets. Renvoie (applications, exacte ?).
     */
    private fun readInstalledApps(tv: SavedTv): Pair<List<InstalledApp>, Boolean> {
        val helper = helperApk()
        val listed = helper?.let { runCatching { adb.listWithHelper(tv.host, it) }.getOrNull() }
        if (listed != null) {
            saveIcons(tv, listed)
            return listed.map { InstalledApp(it.packageName, it.component, it.tvApp, it.label) } to true
        }
        return adb.scanApps(tv.host) to false
    }

    /** Application d'aide embarquée (assets/tvhelper.apk), copiée pour être installée. */
    private fun helperApk(): File? = TvAdb.helperApk(getApplication())

    private fun iconDir(tvId: String) = File(getApplication<Application>().filesDir, "icons/$tvId")

    private fun saveIcons(tv: SavedTv, apps: List<ListedApp>) {
        val dir = iconDir(tv.id)
        dir.deleteRecursively()
        dir.mkdirs()
        for (app in apps) {
            val data = app.iconBase64 ?: continue
            runCatching { File(dir, "${app.packageName}.png").writeBytes(Base64.decode(data, Base64.DEFAULT)) }
        }
    }

    /** Vraie icône d'une application de la TV affichée, si elle a été lue. */
    fun appIcon(packageName: String): ImageBitmap? {
        val tv = _state.value.current ?: return null
        val file = File(iconDir(tv.id), "$packageName.png")
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
    }

    /** Lit sur la TV la liste exacte des applications installées. */
    fun scanApps(silent: Boolean = false) {
        if (notOverIr("La liste des applications")) return
        val tv = _state.value.current ?: return
        val c = catalog ?: return
        if (_state.value.connection != Connection.CONNECTED) {
            say("Allumez d'abord la TV")
            return
        }
        _state.update {
            it.copy(
                scanningApps = true,
                appsMessage = if (silent) "Mise à jour de la liste…" else
                    "Si la TV demande « Autoriser le débogage ? », cochez « Toujours autoriser » " +
                        "puis choisissez OK avec les flèches de cette télécommande (1 minute).",
            )
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { readInstalledApps(tv) } }
            result.onSuccess { (apps, exact) ->
                setAdbAuthorized(tv)
                c.setInstalled(apps)
                val list = c.list()
                _state.update {
                    it.copy(
                        scanningApps = false,
                        apps = list,
                        appsComplete = c.isComplete,
                        appsVersion = it.appsVersion + 1,
                        appsMessage = if (exact) {
                            "${list.size} applications : liste exacte de la TV, avec leurs vrais noms."
                        } else {
                            "${list.size} applications installées (noms approximatifs : la lecture détaillée a échoué)."
                        },
                    )
                }
                if (_state.value.current?.mac == null) detectMac()
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
        prefs.unregisterOnSharedPreferenceChangeListener(backupListener)
        closeSession()
        closePairing()
    }

    private fun clientName(): String = "Télécommande (${Build.MODEL})"

    private companion object {
        /** Ancienneté au-delà de laquelle la liste des applications est relue automatiquement. */
        const val APPS_REFRESH_MS = 5 * 60 * 1000L

        /** Durée pendant laquelle on attend la TV après un réveil par le réseau. */
        const val WAKE_WINDOW_MS = 45_000L
    }
}
