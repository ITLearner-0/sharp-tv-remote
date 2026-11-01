package io.github.itlearner0.tvremote.quick

import io.github.itlearner0.tvremote.i18n.tr

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
        // Widget, notification, tuile et partage : fonctions OmniZap Pro (après l'essai).
        if (!io.github.itlearner0.tvremote.pro.Pro.isUnlocked(app)) {
            toast(app, tr("Fonction OmniZap Pro : ouvrez OmniZap pour la débloquer"))
            done()
            return
        }
        scope.launch {
            try {
                action(app)
            } catch (e: Exception) {
                toast(app, tr("Télécommande : {1}", e.message ?: tr("TV injoignable")))
            } finally {
                done()
            }
        }
    }

    /** Envoie une touche à la TV affichée (Wi-Fi, ou infrarouge si c'est une TV infrarouge ou injoignable). */
    suspend fun key(context: Context, keyCode: Int) {
        val tv = currentTv(context) ?: return toast(context, tr("Aucune TV enregistrée"))
        if (tv.isBt) return bt(context, tv, keyCode)
        if (tv.isIr) return ir(context, tv, keyCode)
        if (tv.brandRemote != null) {
            val remote = brand(context, tv)
            if (remote == null) {
                if (tv.irFallback) return ir(context, tv, keyCode)
                return toast(context, tr("{1} est injoignable", tv.name))
            }
            remote.key(keyCode)
            return
        }
        val session = session(context, tv)
        if (session == null) {
            if (tv.irFallback) return ir(context, tv, keyCode)
            return toast(context, tr("{1} est injoignable", tv.name))
        }
        session.sendKey(keyCode)
    }

    /** Marche / arrêt : touche Power si la TV répond, sinon réveil réseau et infrarouge. */
    suspend fun power(context: Context) {
        val tv = currentTv(context) ?: return toast(context, tr("Aucune TV enregistrée"))
        if (tv.isBt) return bt(context, tv, Keys.POWER)
        if (tv.isIr) return ir(context, tv, Keys.POWER)
        if (tv.brandRemote != null) {
            val remote = brand(context, tv, quick = true)
            if (remote != null) return remote.powerOff()
            tv.mac?.let { WakeOnLan.send(it, tv.host) }
            if (tv.irFallback) ir(context, tv, Keys.WAKEUP)
            return if (tv.mac == null && !tv.irFallback) toast(context, tr("{1} est injoignable", tv.name))
            else toast(context, tr("Allumage de {1}…", tv.name))
        }
        val session = session(context, tv, quick = true)
        if (session != null) {
            session.sendKey(if (session.state.isOn) Keys.POWER else Keys.WAKEUP)
            return
        }
        tv.mac?.let { WakeOnLan.send(it, tv.host) }
        if (tv.irFallback) ir(context, tv, Keys.WAKEUP)
        if (tv.mac == null && !tv.irFallback) toast(context, tr("{1} est injoignable", tv.name))
        else toast(context, tr("Allumage de {1}…", tv.name))
    }

    /** Ouvre un lien (vidéo YouTube, page…) sur la TV. */
    suspend fun open(context: Context, link: String) {
        val tv = currentTv(context) ?: return toast(context, tr("Aucune TV enregistrée"))
        if (tv.offline) return toast(context, tr("Impossible avec une TV pilotée par infrarouge ou Bluetooth"))
        if (tv.brandRemote != null) return toast(context, tr("{1} n'est disponible qu'avec les TV Android et Google TV", tr("Ouvrir sur la TV")))
        val session = session(context, tv) ?: return toast(context, tr("{1} est injoignable : allumez-la d'abord", tv.name))
        session.launchApp(link)
        toast(context, tr("Ouverture sur {1}…", tv.name))
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
        if (!remote.isConnectedTo(tv.btAddress)) return toast(context, tr("{1} : Bluetooth non connecté", tv.name))
        remote.key(keyCode)
    }

    private fun ir(context: Context, tv: SavedTv, keyCode: Int) {
        val codeset = IrDatabase.load(context).codeset(tv.irCodeset) ?: return
        val name = IrDatabase.irKeysFor(keyCode).firstOrNull { it in codeset.keys } ?: return
        if (!IrBlaster(context).send(codeset.keys.getValue(name))) toast(context, tr("Pas d'émetteur infrarouge"))
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

    /** Connexion de l'application à une TV Samsung / LG (renseignée par l'écran principal), ou null. */
    @Volatile var appBrand: io.github.itlearner0.tvremote.brand.BrandRemote? = null
    private var ownBrand: io.github.itlearner0.tvremote.brand.BrandRemote? = null

    /** TV Samsung / LG : réutilise la connexion ouverte, sinon se connecte avec la clé enregistrée. */
    private suspend fun brand(context: Context, tv: SavedTv, quick: Boolean = false): io.github.itlearner0.tvremote.brand.BrandRemote? =
        mutex.withLock {
            appBrand?.takeIf { it.isOpen }?.let { return@withLock it }
            ownBrand?.takeIf { it.isOpen }?.let {
                scheduleClose()
                return@withLock it
            }
            val b = tv.brandRemote ?: return@withLock null
            val remote = io.github.itlearner0.tvremote.brand.BrandRemote.create(b, tv.host, tv.brandKey, "OmniZap", {})
            runCatching { remote.open(timeoutMs = if (quick) 3_000 else 6_000) }.getOrElse {
                remote.close()
                return@withLock null
            }
            ownBrand = remote
            scheduleClose()
            remote
        }

    private fun scheduleClose() {
        idleClose?.cancel()
        idleClose = scope.launch {
            delay(120_000)
            mutex.withLock {
                own?.close()
                own = null
                ownBrand?.close()
                ownBrand = null
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
