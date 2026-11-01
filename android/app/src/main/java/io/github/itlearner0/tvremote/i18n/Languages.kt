// Fichier produit par tools/i18n/build_langs.py : ne pas modifier.
package io.github.itlearner0.tvremote.i18n

/** Dictionnaires des langues autres que le français et l'anglais. */
internal fun dictionary(language: String): Map<String, String>? = when (language) {
    "am" -> L_AM
    "ar" -> L_AR
    "ary" -> L_ARY
    "de" -> L_DE
    "es" -> L_ES
    "ha" -> L_HA
    "hi" -> L_HI
    "it" -> L_IT
    "ln" -> L_LN
    "pt" -> L_PT
    "sw" -> L_SW
    "wo" -> L_WO
    "yo" -> L_YO
    else -> null
}
