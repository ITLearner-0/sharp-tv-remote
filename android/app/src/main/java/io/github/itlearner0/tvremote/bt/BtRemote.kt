package io.github.itlearner0.tvremote.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.itlearner0.tvremote.protocol.BtHidKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

/** État du mode Bluetooth. */
data class BtState(
    /** Le téléphone sait se présenter comme télécommande Bluetooth (Android 9+, fonction non désactivée). */
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val permitted: Boolean = false,
    /** Le téléphone est enregistré comme télécommande auprès d'Android. */
    val registered: Boolean = false,
    /** Adresse et nom de la TV connectée, ou null. */
    val connectedAddress: String? = null,
    val connectedName: String? = null,
    val connecting: Boolean = false,
    val message: String? = null,
    /** Étapes de la préparation (diagnostic affiché dans l'assistant). */
    val log: List<String> = emptyList(),
)

/**
 * Télécommande Bluetooth : le téléphone se présente à la TV comme un clavier
 * doublé d'une télécommande multimédia (HID). Aucune application à installer sur
 * la TV, aucun réseau : fonctionne aussi avec les TV non connectées et la plupart
 * des TV, boîtiers et consoles qui acceptent un clavier Bluetooth.
 */
// Permissions vérifiées par permitted(), version d'Android par init() (Android 9+ pour le mode HID).
@SuppressLint("MissingPermission", "NewApi")
object BtRemote {
    private val _state = MutableStateFlow(BtState())
    val state: StateFlow<BtState> = _state.asStateFlow()

    private var app: Context? = null
    private var hid: BluetoothHidDevice? = null
    private var adapter: BluetoothAdapter? = null
    private var device: BluetoothDevice? = null
    /** Adresse à laquelle se connecter dès que l'enregistrement est fait. */
    @Volatile private var wanted: String? = null
    private val executor = Executors.newSingleThreadExecutor()
    /** Demande de service HID en cours : une seule à la fois (Android n'accepte qu'un enregistrement). */
    @Volatile private var proxyRequested = false
    private var retriedRegistration = false

    private fun log(text: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())
        _state.update { it.copy(log = (it.log + "$time  $text").takeLast(12)) }
    }

    private fun fail(message: String) {
        log(message)
        _state.update { it.copy(connecting = false, message = message) }
    }
    private val sender = Executors.newSingleThreadExecutor()

    /** Permissions à demander à l'utilisateur. */
    val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Prépare le Bluetooth (à rappeler après l'octroi des permissions). [connectTo] : TV à joindre. */
    fun init(context: Context, connectTo: String? = null) {
        val ctx = context.applicationContext
        app = ctx
        if (connectTo != null) wanted = connectTo
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            _state.update { it.copy(supported = false, message = "Le Bluetooth demande Android 9 ou plus récent") }
            return
        }
        val bt = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        adapter = bt
        val permitted = permitted(ctx)
        _state.update { it.copy(supported = bt != null, enabled = bt?.isEnabled == true, permitted = permitted) }
        if (bt == null || !permitted || !bt.isEnabled) return
        val proxy = hid
        if (proxy != null) {
            if (_state.value.registered) wanted?.let { connect(it) }
            return
        }
        if (proxyRequested) return
        proxyRequested = true
        log("Demande du service « télécommande Bluetooth » à Android (Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL})")
        val ok = bt.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                log("Service obtenu")
                val h = proxy as BluetoothHidDevice
                hid = h
                register(h)
            }

            override fun onServiceDisconnected(profile: Int) {
                log("Service perdu")
                hid = null
                proxyRequested = false
                _state.update { it.copy(registered = false, connectedAddress = null, connectedName = null) }
            }
        }, BluetoothProfile.HID_DEVICE)
        if (!ok) {
            proxyRequested = false
            _state.update { it.copy(supported = false) }
            fail("Ce téléphone ne permet pas le mode télécommande Bluetooth (refusé par Android)")
            return
        }
        // Pas de réponse au bout de 8 s : fonction probablement désactivée par le fabricant.
        main.postDelayed({
            if (!_state.value.registered && _state.value.message == null) {
                fail(
                    "Android ne répond pas à la demande de mode télécommande Bluetooth. Sur certains téléphones, " +
                        "le fabricant a désactivé cette fonction. Touchez « Réessayer » après avoir éteint puis rallumé le Bluetooth.",
                )
            }
        }, 8000)
    }

    /** Relance complète : désenregistrement, fermeture du service, nouvelle demande. */
    fun reset(context: Context) {
        val h = hid
        runCatching { h?.unregisterApp() }
        runCatching { if (h != null) adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h) }
        hid = null
        device = null
        proxyRequested = false
        retriedRegistration = false
        attempts = 0
        reRegistered = false
        _state.update { it.copy(registered = false, connecting = false, connectedAddress = null, connectedName = null, message = null, log = emptyList()) }
        init(context, wanted)
    }

    private fun register(h: BluetoothHidDevice) {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "OmniZap",
            "Télécommande TV OmniZap",
            "OmniZap",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            BtHidKeys.DESCRIPTOR,
        )
        val registered = h.registerApp(sdp, null, null, executor, object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                log(if (registered) "Téléphone enregistré comme télécommande" else "Enregistrement retiré par Android")
                _state.update { it.copy(registered = registered, message = if (registered) null else it.message) }
                if (!registered) return
                val target = pluggedDevice?.address ?: wanted
                target?.let { connect(it) }
            }

            override fun onConnectionStateChanged(d: BluetoothDevice, state: Int) {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        device = d
                        _state.update {
                            it.copy(connectedAddress = d.address, connectedName = d.name ?: d.address, connecting = false, message = null)
                        }
                    }
                    BluetoothProfile.STATE_CONNECTING -> _state.update { it.copy(connecting = true) }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (device?.address == d.address) device = null
                        _state.update {
                            when {
                                // Refus pendant la connexion : la TV n'accepte pas (encore) le téléphone comme télécommande.
                                it.connecting && it.connectedAddress == null -> it.copy(
                                    connecting = false,
                                    message = "La TV a refusé la connexion Bluetooth. Vérifiez que l'association a été " +
                                        "acceptée sur la TV, puis touchez de nouveau la TV dans « Appareils déjà associés ».",
                                )
                                it.connectedAddress == d.address -> it.copy(connectedAddress = null, connectedName = null, connecting = false)
                                else -> it
                            }
                        }
                    }
                }
            }
        })
        log("Enregistrement demandé : " + if (registered) "accepté" else "réponse négative, attente de confirmation")
        if (registered) return
        // Certains téléphones (Samsung) répondent « non » puis confirment l'enregistrement juste après :
        // on attend la confirmation avant de conclure.
        main.postDelayed({
            if (_state.value.registered) return@postDelayed
            // Un enregistrement précédent de l'application peut subsister : on le retire et on réessaie une fois.
            if (!retriedRegistration) {
                retriedRegistration = true
                runCatching { h.unregisterApp() }
                main.postDelayed({ register(h) }, 1000)
                return@postDelayed
            }
            fail(
                "Le mode télécommande Bluetooth du téléphone est déjà pris par une autre application " +
                    "(par exemple l'ancienne « Télécommande TV », ou une application clavier/souris Bluetooth) : " +
                    "désinstallez-la, éteignez puis rallumez le Bluetooth, puis touchez « Réessayer ».",
            )
        }, 3000)
    }

    /** Se connecte à la TV [address] (déjà associée au téléphone). */
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var attempts = 0
    private var reRegistered = false

    fun connect(address: String) {
        if (wanted != address) {
            attempts = 0
            reRegistered = false
        }
        wanted = address
        val h = hid ?: return init(app ?: return, address)
        val bt = adapter ?: return
        if (_state.value.connectedAddress == address) return
        // Le téléphone doit d'abord être enregistré comme télécommande : la connexion part ensuite d'elle-même.
        if (!_state.value.registered) {
            // N'écrase pas un message d'erreur déjà affiché.
            _state.update { it.copy(connecting = it.message == null) }
            return
        }
        val target = runCatching { bt.getRemoteDevice(address) }.getOrNull() ?: return
        if (target.bondState != BluetoothDevice.BOND_BONDED) {
            _state.update {
                it.copy(connecting = false, message = "La TV n'est pas associée au téléphone : recherchez-la et acceptez l'association.")
            }
            return
        }
        _state.update { it.copy(connecting = true, message = null) }
        if (h.connect(target)) return
        // Refus immédiat : nouvel essai, puis réenregistrement de la télécommande.
        attempts++
        when {
            attempts < 3 -> main.postDelayed({ if (wanted == address) connect(address) }, 1500)
            !reRegistered -> {
                reRegistered = true
                attempts = 0
                _state.update { it.copy(registered = false, message = "Nouvel essai…") }
                runCatching { h.unregisterApp() }
                main.postDelayed({ register(h) }, 800)
            }
            else -> _state.update {
                it.copy(
                    connecting = false,
                    message = "Android refuse la connexion Bluetooth à cette TV. Désinstallez l'ancienne application " +
                        "« Télécommande TV » si elle est encore là, redémarrez le Bluetooth du téléphone, puis réessayez.",
                )
            }
        }
    }

    fun disconnect() {
        wanted = null
        val d = device ?: return
        hid?.disconnect(d)
    }

    /** Appareils déjà associés au téléphone (TV, boîtiers…). */
    fun bondedDevices(): List<BluetoothDevice> =
        runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList())
            .sortedBy { it.name ?: it.address }

    fun isConnectedTo(address: String?): Boolean = address != null && _state.value.connectedAddress == address

    /** Envoie une touche de télécommande ; false si elle n'existe pas en Bluetooth ou si la TV n'est pas connectée. */
    fun key(keyCode: Int): Boolean {
        val key = BtHidKeys.forKeyCode(keyCode) ?: return false
        return press(key)
    }

    /** Tape [text] comme sur un clavier ; renvoie les caractères qui n'ont pas pu être tapés. */
    fun type(text: String): List<Char> {
        val missing = mutableListOf<Char>()
        val keys = text.mapNotNull { c -> BtHidKeys.forChar(c).also { if (it == null) missing += c } }
        sender.execute {
            for (key in keys) {
                sendPress(key)
                Thread.sleep(15)
            }
        }
        return missing
    }

    private fun press(key: BtHidKeys.Key): Boolean {
        if (device == null || hid == null) return false
        sender.execute { sendPress(key) }
        return true
    }

    private fun sendPress(key: BtHidKeys.Key) {
        val h = hid ?: return
        val d = device ?: return
        runCatching {
            when (key) {
                is BtHidKeys.Key.Keyboard -> {
                    h.sendReport(d, BtHidKeys.REPORT_KEYBOARD, BtHidKeys.keyboardReport(key))
                    Thread.sleep(25)
                    h.sendReport(d, BtHidKeys.REPORT_KEYBOARD, BtHidKeys.keyboardReport(null))
                }
                is BtHidKeys.Key.Consumer -> {
                    h.sendReport(d, BtHidKeys.REPORT_CONSUMER, BtHidKeys.consumerReport(key.usage))
                    Thread.sleep(40)
                    h.sendReport(d, BtHidKeys.REPORT_CONSUMER, BtHidKeys.consumerReport(0))
                }
            }
        }
    }
}
