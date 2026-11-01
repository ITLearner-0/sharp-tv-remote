package io.github.itlearner0.tvremote.i18n

import java.util.Locale

/**
 * Traduction des textes de l'application. Le français est la langue du code ;
 * pour une autre langue, son dictionnaire ([dictionary], tools/i18n/build_langs.py),
 * à défaut le texte anglais ([EN], tools/i18n/build_en.py).
 * Les valeurs variables s'écrivent {1}, {2}… : tr("Connecté à {1}", nom).
 */
fun tr(fr: String, vararg args: Any?): String {
    val language = Locale.getDefault().language
    val text = when (language) {
        "fr" -> fr
        // Autres langues : leur dictionnaire, puis l'anglais (journaux de diagnostic).
        else -> dictionary(language)?.get(fr) ?: EN[fr] ?: fr
    }
    if (args.isEmpty()) return text
    var result = text
    args.forEachIndexed { i, value -> result = result.replace("{${i + 1}}", value.toString()) }
    return result
}

/** Le téléphone (ou la langue choisie pour OmniZap) est en français. */
fun french(): Boolean = Locale.getDefault().language == "fr"
