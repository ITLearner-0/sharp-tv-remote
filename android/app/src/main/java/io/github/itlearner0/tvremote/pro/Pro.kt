package io.github.itlearner0.tvremote.pro

import io.github.itlearner0.tvremote.i18n.tr

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.itlearner0.tvremote.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit

/** Fonctions réservées à OmniZap Pro (la télécommande de base reste gratuite). */
enum class ProFeature(val label: String) {
    MULTI_TV(tr("Plusieurs TV")),
    APPS(tr("Les applications de la TV, avec leurs icônes")),
    VOICE(tr("Commandes vocales")),
    MEDIA(tr("Photos et vidéos sur la TV")),
    MIRROR(tr("Duplication de l'écran, avec le son")),
    TV_VIEW(tr("Voir la TV sur le téléphone")),
    BLUETOOTH(tr("Télécommande Bluetooth")),
    QUICK(tr("Widget, notification et réglages rapides")),
}

/** Offre affichée ; [feature] : fonction verrouillée qui l'a déclenchée (null : ouverture libre). */
data class ProOffer(val feature: ProFeature?)

data class ProState(
    val purchased: Boolean = false,
    /** Fin de l'essai gratuit (millisecondes). */
    val trialEndsAt: Long = 0,
    /** Prix affiché par Google Play (null hors Play Store). */
    val price: String? = null,
    val billingReady: Boolean = false,
    val busy: Boolean = false,
) {
    val trialActive: Boolean get() = !purchased && System.currentTimeMillis() < trialEndsAt
    val unlocked: Boolean get() = purchased || trialActive
    val trialDaysLeft: Int
        get() = if (!trialActive) 0 else ((trialEndsAt - System.currentTimeMillis()) / TimeUnit.DAYS.toMillis(1) + 1).toInt()
}

/**
 * OmniZap Pro : achat unique par Google Play (produit [PRODUCT_ID]), précédé
 * d'un essai gratuit de [TRIAL_DAYS] jours de toutes les fonctions, qui démarre
 * au premier lancement. Pas d'abonnement, pas de publicité.
 */
object Pro {
    const val PRODUCT_ID = "omnizap_pro"
    const val TRIAL_DAYS = 7
    /** Prix prévu au lancement, affiché tant que Google Play n'a pas donné le sien. */
    const val LAUNCH_PRICE = "9,99 €"

    private val _state = MutableStateFlow(ProState())
    val state: StateFlow<ProState> = _state.asStateFlow()

    /** Fonction Pro demandée alors qu'elle est verrouillée : l'offre s'affiche. */
    private val _offer = MutableStateFlow<ProOffer?>(null)
    val offer: StateFlow<ProOffer?> = _offer.asStateFlow()

    private var app: Context? = null
    private var client: BillingClient? = null
    private var product: ProductDetails? = null

    fun init(context: Context) {
        if (app != null) return
        val ctx = context.applicationContext
        app = ctx
        val prefs = prefs(ctx)
        if (!prefs.contains(KEY_TRIAL_START)) prefs.edit().putLong(KEY_TRIAL_START, System.currentTimeMillis()).apply()
        _state.update {
            it.copy(
                purchased = prefs.getBoolean(KEY_PURCHASED, false),
                trialEndsAt = prefs.getLong(KEY_TRIAL_START, 0) + TimeUnit.DAYS.toMillis(TRIAL_DAYS.toLong()),
            )
        }
        connectBilling(ctx)
    }

    fun isUnlocked(context: Context): Boolean {
        init(context)
        return _state.value.unlocked
    }

    /** true si la fonction est disponible ; sinon affiche l'offre Pro et renvoie false. */
    fun require(context: Context, feature: ProFeature): Boolean {
        if (isUnlocked(context)) return true
        _offer.value = ProOffer(feature)
        return false
    }

    fun showOffer(feature: ProFeature? = null) {
        _offer.value = ProOffer(feature)
    }

    fun dismissOffer() {
        _offer.value = null
    }

    /** Version de test (hors Play Store) : déblocage manuel pour les essais. */
    val testUnlockAvailable: Boolean get() = BuildConfig.TEST_UNLOCK

    fun testUnlock(context: Context) {
        if (!BuildConfig.TEST_UNLOCK) return
        setPurchased(context, true)
    }

    fun buy(activity: Activity) {
        val details = product
        val billing = client
        if (details == null || billing == null || !billing.isReady) {
            Toast.makeText(
                activity,
                tr("L'achat se fera par Google Play, quand OmniZap y sera publié. En attendant, profitez de l'essai."),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        _state.update { it.copy(busy = true) }
        billing.launchBillingFlow(activity, params)
    }

    /** Vérifie auprès de Google Play les achats déjà faits (nouveau téléphone, réinstallation). */
    fun restore(context: Context) {
        val billing = client
        if (billing == null || !billing.isReady) {
            Toast.makeText(context, tr("Google Play n'est pas joignable"), Toast.LENGTH_SHORT).show()
            return
        }
        queryPurchases(billing, notify = true)
    }

    private fun connectBilling(ctx: Context) {
        val billing = BillingClient.newBuilder(ctx)
            .setListener { result, purchases -> onPurchases(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = billing
        runCatching {
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                    _state.update { it.copy(billingReady = true) }
                    queryProduct(billing)
                    queryPurchases(billing, notify = false)
                }

                override fun onBillingServiceDisconnected() {
                    _state.update { it.copy(billingReady = false) }
                }
            })
        }
    }

    private fun queryProduct(billing: BillingClient) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        billing.queryProductDetailsAsync(params) { _, list ->
            val details = list.firstOrNull() ?: return@queryProductDetailsAsync
            product = details
            _state.update { it.copy(price = details.oneTimePurchaseOfferDetails?.formattedPrice) }
        }
    }

    private fun queryPurchases(billing: BillingClient, notify: Boolean) {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billing.queryPurchasesAsync(params) { _, purchases ->
            val owned = purchases.any { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            purchases.forEach { acknowledge(billing, it) }
            val ctx = app ?: return@queryPurchasesAsync
            // Un déblocage de test n'est pas annulé par Google Play.
            if (owned) {
                setPurchased(ctx, true, fromPlay = true)
            } else if (!prefs(ctx).getBoolean(KEY_TEST, false)) {
                setPurchased(ctx, false, fromPlay = true)
            }
            if (notify) {
                app?.let {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            it,
                            if (owned) tr("Achat OmniZap Pro retrouvé, merci !") else tr("Aucun achat OmniZap Pro sur ce compte Google"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun onPurchases(result: BillingResult, purchases: List<Purchase>?) {
        _state.update { it.copy(busy = false) }
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        val billing = client ?: return
        for (purchase in purchases) {
            if (PRODUCT_ID !in purchase.products || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            acknowledge(billing, purchase)
            app?.let { setPurchased(it, true, fromPlay = true) }
            _offer.value = null
        }
    }

    private fun acknowledge(billing: BillingClient, purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        billing.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { }
    }

    private fun setPurchased(context: Context, purchased: Boolean, fromPlay: Boolean = false) {
        prefs(context).edit()
            .putBoolean(KEY_PURCHASED, purchased)
            .putBoolean(KEY_TEST, purchased && !fromPlay)
            .apply()
        _state.update { it.copy(purchased = purchased) }
        if (purchased) _offer.value = null
    }

    private fun prefs(context: Context) = context.getSharedPreferences("pro", Context.MODE_PRIVATE)

    private const val KEY_TRIAL_START = "trial_start"
    private const val KEY_PURCHASED = "purchased"
    private const val KEY_TEST = "test_unlock"
}
