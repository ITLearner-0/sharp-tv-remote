package io.github.itlearner0.tvremote.quick

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * « Partager → Télécommande TV » : ouvre sur la TV la vidéo ou la page partagée
 * (YouTube, Netflix, site web…). Sans écran : l'envoi se fait en arrière-plan.
 */
class ShareToTvActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val link = ShareLinks.toTvLink(text)
        if (link == null) {
            android.widget.Toast.makeText(this, "Aucun lien à ouvrir sur la TV", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            QuickRemote.run(this) { QuickRemote.open(it, link) }
        }
        finish()
    }
}

/** Liens partagés → liens que les applications de la TV savent ouvrir. */
object ShareLinks {
    private val URL = Regex("""https?://\S+""")
    private val YOUTUBE_SHORT = Regex("""https?://youtu\.be/([\w-]{6,})""")
    private val YOUTUBE_ID = Regex("""[?&]v=([\w-]{6,})""")
    private val YOUTUBE_SHORTS = Regex("""youtube\.com/shorts/([\w-]{6,})""")

    fun toTvLink(text: String): String? {
        val url = URL.find(text)?.value?.trimEnd('.', ',', ')', '»') ?: return null
        val youtubeId = YOUTUBE_SHORT.find(url)?.groupValues?.get(1)
            ?: YOUTUBE_SHORTS.find(url)?.groupValues?.get(1)
            ?: if ("youtube.com" in url) YOUTUBE_ID.find(url)?.groupValues?.get(1) else null
        if (youtubeId != null) return "https://www.youtube.com/watch?v=$youtubeId"
        return url
    }
}
