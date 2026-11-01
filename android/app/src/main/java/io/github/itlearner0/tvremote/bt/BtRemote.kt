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
        val ok = bt.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val h = proxy as BluetoothHidDevice
                hid = h
                register(h)
            }

            override fun onServiceDisconnected(profile: Int) {
                hid = null
                _state.update { it.copy(registered = false, connectedAddress = null, connectedName = null) }
            }
        }, BluetoothProfile.HID_DEVICE)
        if (!ok) _state.update { it.copy(supported = false, message = "Ce téléphone ne permet pas le mode télécommande Bluetooth") }
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
                _state.update { it.copy(registered = registered) }
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
                            if (it.connectedAddress == d.address || it.connecting) {
                                it.copy(connectedAddress = null, connectedName = null, connecting = false)
                            } else {
                                it
                            }
                        }
                    }
                }
            }
        })
        if (!registered) {
            _state.update {
                it.copy(message = "Le mode télécommande Bluetooth est déjà utilisé par une autre application, ou indisponible")
            }
        }
    }

    /** Se connecte à la TV [address] (déjà associée au téléphone). */
    fun connect(address: String) {
        wanted = address
        val h = hid ?: return init(app ?: return, address)
        val bt = adapter ?: return
        if (_state.value.connectedAddress == address) return
        val target = runCatching { bt.getRemoteDevice(address) }.getOrNull() ?: return
        _state.update { it.copy(connecting = true, message = null) }
        if (!h.connect(target)) {
            _state.update { it.copy(connecting = false, message = "Connexion Bluetooth refusée") }
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
