package io.github.itlearner0.tvremote.quick

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.itlearner0.tvremote.data.SavedTv
import io.github.itlearner0.tvremote.data.TvStore
import io.github.itlearner0.tvremote.ir.IrBlaster
import io.github.itlearner0.tvremote.ir.IrDatabase
import io.github.itlearner0.tvremote.protocol.ClientIdentity
import io.github.itlearner0.tvremote.protocol.Keys
import io.github.itlearner0.tvremote.protocol.RemoteSession
import io.github.itlearner0.tvremote.protocol.WakeOnLan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Télécommande « hors application » : widget, notification, tuile des
 * réglages rapides et partage vers la TV. Réutilise la connexion de
 * l'application si elle est ouverte, sinon se connecte à la demande et se
 * déconnecte après deux minutes d'inactivité.
 */
object QuickRemote {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var own: RemoteSession? = null
    private var idleClose: Job? = null

    /** Connexion ouverte par l'application elle-même (renseignée par l'écran principal). */
    @Volatile var appSession: RemoteSession? = null

    fun currentTv(context: Context): SavedTv? = TvStore(prefs(context)).current()

    /** Lance [action] en arrière-plan et appelle [done] à la fin (pour les récepteurs de diffusion). */
    fun run(context: Context, done: () -> Unit = {}, action: suspend (Context) -> Unit) {
        val app = context.applicationContext
        scope.launch {
            try {
                action(app)
            } catch (e: Exception) {
                toast(app, "Télécommande : ${e.message ?: "TV injoignable"}")
            } finally {
                done()
            }
        }
    }

    /** Envoie une touche à la TV affichée (Wi-Fi, ou infrarouge si c'est une TV infrarouge ou injoignable). */
    suspend fun key(context: Context, keyCode: Int) {
        val tv = currentTv(context) ?: return toast(context, "Aucune TV enregistrée")
        if (tv.isBt) return bt(context, tv, keyCode)
        if (tv.isIr) return ir(context, tv, keyCode)
        val session = session(context, tv)
        if (session == null) {
            if (tv.irFallback) return ir(context, tv, keyCode)
            return toast(context, "${tv.name} est injoignable")
        }
        session.sendKey(keyCode)
    }

    /** Marche / arrêt : touche Power si la TV répond, sinon réveil réseau et infrarouge. */
    suspend fun power(context: Context) {
        val tv = currentTv(context) ?: return toast(context, "Aucune TV enregistrée")
        if (tv.isBt) return bt(context, tv, Keys.POWER)
        if (tv.isIr) return ir(context, tv, Keys.POWER)
        val session = session(context, tv, quick = true)
        if (session != null) {
            session.sendKey(if (session.state.isOn) Keys.POWER else Keys.WAKEUP)
            return
        }
        tv.mac?.let { WakeOnLan.send(it, tv.host) }
        if (tv.irFallback) ir(context, tv, Keys.WAKEUP)
        if (tv.mac == null && !tv.irFallback) toast(context, "${tv.name} est injoignable")
        else toast(context, "Allumage de ${tv.name}…")
    }

    /** Ouvre un lien (vidéo YouTube, page…) sur la TV. */
    suspend fun open(context: Context, link: String) {
        val tv = currentTv(context) ?: return toast(context, "Aucune TV enregistrée")
        if (tv.offline) return toast(context, "Impossible avec une TV pilotée par infrarouge ou Bluetooth")
        val session = session(context, tv) ?: return toast(context, "${tv.name} est injoignable : allumez-la d'abord")
        session.launchApp(link)
        toast(context, "Ouverture sur ${tv.name}…")
    }

    /** TV Bluetooth : connexion si besoin (quelques secondes), puis la touche. */
    private suspend fun bt(context: Context, tv: SavedTv, keyCode: Int) {
        val remote = io.github.itlearner0.tvremote.bt.BtRemote
        if (!remote.isConnectedTo(tv.btAddress)) {
            remote.init(context, tv.btAddress)
            repeat(30) {
                if (remote.isConnectedTo(tv.btAddress)) return@repeat
                delay(200)
            }
        }
        if (!remote.isConnectedTo(tv.btAddress)) return toast(context, "${tv.name} : Bluetooth non connecté")
        remote.key(keyCode)
    }

    private fun ir(context: Context, tv: SavedTv, keyCode: Int) {
        val codeset = IrDatabase.load(context).codeset(tv.irCodeset) ?: return
        val name = IrDatabase.irKeysFor(keyCode).firstOrNull { it in codeset.keys } ?: return
        if (!IrBlaster(context).send(codeset.keys.getValue(name))) toast(context, "Pas d'émetteur infrarouge")
    }

    private suspend fun session(context: Context, tv: SavedTv, quick: Boolean = false): RemoteSession? =
        mutex.withLock {
            appSession?.takeIf { it.isOpen }?.let { return@withLock it }
            own?.takeIf { it.isOpen }?.let {
                scheduleClose()
                return@withLock it
            }
            val identity = ClientIdentity.loadOrCreate(context.filesDir)
            val session = RemoteSession(tv.host, identity, timeoutMs = if (quick) 3_000 else 6_000)
            runCatching { session.connect() }.getOrNull() ?: return@withLock null
            own = session
            scheduleClose()
            session
        }

    private fun scheduleClose() {
        idleClose?.cancel()
        idleClose = scope.launch {
            delay(120_000)
            mutex.withLock {
                own?.close()
                own = null
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("tv", Context.MODE_PRIVATE)

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
