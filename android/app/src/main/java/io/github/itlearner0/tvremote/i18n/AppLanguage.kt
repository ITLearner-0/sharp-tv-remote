package io.github.itlearner0.tvremote.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Langue d'OmniZap, choisie au premier lancement (ou dans « Mes TV ») ;
 * par défaut celle du téléphone. Sur Android 13+, le choix est aussi celui du
 * réglage système « Langue de l'application ».
 */
object AppLanguage {
    /** Langues disponibles : code et nom dans la langue elle-même. */
    val SUPPORTED = listOf(
        "fr" to "Français",
        "en" to "English",
    )

    private const val PREFS = "ui"
    private const val KEY = "language"

    /** L'utilisateur a déjà choisi sa langue (sinon : écran de choix au lancement). */
    fun chosen(context: Context): Boolean = prefs(context).contains(KEY)

    /** Langue en cours, parmi [SUPPORTED] (anglais si celle du téléphone n'y est pas). */
    fun current(): String {
        val lang = Locale.getDefault().language
        return SUPPORTED.firstOrNull { it.first == lang }?.first ?: "en"
    }

    /** Au démarrage de l'application : applique la langue choisie (avant Android 13). */
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return // géré par Android
        prefs(context).getString(KEY, null)?.takeIf { it.isNotEmpty() }?.let { Locale.setDefault(Locale(it)) }
    }

    /** Choisit la langue [code] ; l'écran doit ensuite être recréé. */
    fun set(context: Context, code: String) {
        prefs(context).edit().putString(KEY, code).apply()
        Locale.setDefault(Locale(code))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(code)
        }
    }

    /**
     * Choisit [code] et redémarre OmniZap si la langue change : certains textes sont
     * préparés une seule fois au démarrage.
     */
    fun switchTo(activity: android.app.Activity, code: String) {
        val changed = code != Locale.getDefault().language
        set(activity, code)
        if (!changed) return
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
        activity.startActivity(
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        Runtime.getRuntime().exit(0)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
