package io.github.itlearner0.tvremote.protocol

import java.text.Normalizer

/** Interprétation d'une phrase dictée : ouvrir une application, ou rechercher. */
object VoiceCommands {
    private val OPEN = Regex("""^(?:lance|lancer|ouvre|ouvrir|démarre|démarrer|mets|mettre|va sur|aller sur)\s+(.+)$""", RegexOption.IGNORE_CASE)

    /** Nom de l'application demandée (« lance Netflix » → « Netflix »), ou null. */
    fun appRequest(phrase: String): String? =
        OPEN.find(phrase.trim())?.groupValues?.get(1)?.trim()?.removePrefix("l'")?.removePrefix("la ")?.removePrefix("le ")
            ?.trim()?.ifBlank { null }

    /** Meilleure correspondance parmi [labels] pour [request], ou null. */
    fun bestMatch(request: String, labels: List<String>): String? {
        val wanted = simplify(request)
        if (wanted.isEmpty()) return null
        return labels.firstOrNull { simplify(it) == wanted }
            ?: labels.firstOrNull { simplify(it).startsWith(wanted) || wanted.startsWith(simplify(it)) }
            ?: labels.firstOrNull { simplify(it).contains(wanted) }
            ?: labels.firstOrNull { label -> wanted.split(' ').all { it in simplify(label) } }
    }

    private fun simplify(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex(" +"), " ").trim()
}
