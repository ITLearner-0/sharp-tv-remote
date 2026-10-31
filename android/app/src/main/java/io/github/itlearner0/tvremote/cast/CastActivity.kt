package io.github.itlearner0.tvremote.cast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.itlearner0.tvremote.ui.Accent
import io.github.itlearner0.tvremote.ui.BackgroundBrush
import io.github.itlearner0.tvremote.ui.BodyBrush
import io.github.itlearner0.tvremote.ui.BorderColor
import io.github.itlearner0.tvremote.ui.KeyColor
import io.github.itlearner0.tvremote.ui.Muted
import io.github.itlearner0.tvremote.ui.OnColor
import io.github.itlearner0.tvremote.ui.RemoteTheme

/** Écran d'envoi des photos et vidéos du téléphone vers la TV (Google Cast). */
class CastActivity : ComponentActivity() {
    private val vm: CastViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.setItems(sharedUris(intent))
        setContent {
            RemoteTheme { CastScreen(vm, onClose = ::finish) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.setItems(sharedUris(intent))
    }

    companion object {
        fun start(context: Context, uris: List<Uri>) {
            val intent = Intent(context, CastActivity::class.java)
                .putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (uris.isNotEmpty()) {
                val clip = android.content.ClipData.newRawUri("media", uris.first())
                uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                intent.clipData = clip
            }
            context.startActivity(intent)
        }

        private const val EXTRA_URIS = "uris"

        /** Fichiers transmis par la galerie (partage simple ou multiple) ou par l'application. */
        @Suppress("DEPRECATION")
        fun sharedUris(intent: Intent?): List<Uri> {
            intent ?: return emptyList()
            intent.getParcelableArrayListExtra<Uri>(EXTRA_URIS)?.let { return it }
            return when (intent.action) {
                Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                else -> emptyList()
            }
        }
    }
}

@Composable
private fun CastScreen(vm: CastViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        vm.setItems(uris)
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            vm.consumeMessage()
            snackbar.showSnackbar(it)
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        modifier = Modifier.background(BackgroundBrush),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cast, contentDescription = null, tint = Accent)
                    Text(
                        "  Photos et vidéos sur la TV",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text("Fermer") }
                }

                Panel {
                    if (!state.available) {
                        Text("Google Cast n'est pas disponible sur ce téléphone.", color = MaterialTheme.colorScheme.error)
                    } else if (state.connectedTo != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(OnColor))
                            Text("  Connecté à ${state.connectedTo}", modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::disconnect) { Text("Arrêter") }
                        }
                    } else {
                        Text("Choisissez la TV", fontWeight = FontWeight.SemiBold)
                        if (state.devices.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    "  Recherche des TV compatibles Google Cast (Chromecast intégré)…",
                                    color = Muted,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        for (device in state.devices) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(KeyColor)
                                    .clickable { vm.connect(device) }
                                    .padding(14.dp),
                            ) {
                                Icon(Icons.Filled.Tv, contentDescription = null)
                                Text("  ${device.name}", modifier = Modifier.weight(1f))
                                if (state.connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }

                Panel {
                    if (state.items.isEmpty()) {
                        Text("Aucune photo ni vidéo choisie.", color = Muted)
                    } else {
                        val item = state.items[state.index]
                        Text("${state.index + 1} / ${state.items.size}", color = Muted, fontSize = 12.sp)
                        Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Control(Icons.Filled.SkipPrevious, "Précédente", vm::previous)
                            if (item.isVideo) {
                                Control(Icons.Filled.FastRewind, "Reculer de 10 s") { vm.seekBy(-10_000) }
                                Control(Icons.Filled.PlayArrow, "Lecture / pause", vm::playPause)
                                Control(Icons.Filled.FastForward, "Avancer de 30 s") { vm.seekBy(30_000) }
                            }
                            Control(Icons.Filled.SkipNext, "Suivante", vm::next)
                        }
                        if (state.items.size > 1) {
                            OutlinedButton(onClick = vm::toggleSlideshow, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Slideshow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(if (state.slideshow) "  Arrêter le diaporama" else "  Diaporama (6 s par photo)")
                            }
                        }
                        for ((i, media) in state.items.withIndex()) {
                            Text(
                                (if (media.isVideo) "🎬  " else "🖼  ") + media.name,
                                color = if (i == state.index) Color.White else Muted,
                                fontWeight = if (i == state.index) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable { vm.show(i) }.padding(vertical = 4.dp),
                            )
                        }
                    }
                    Button(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (state.items.isEmpty()) "  Choisir des photos et vidéos" else "  Choisir d'autres fichiers")
                    }
                }
                if (state.log.isNotEmpty()) {
                    Panel {
                        Text("Journal", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        for (line in state.log) Text(line, color = Muted, fontSize = 11.sp)
                    }
                }
                Text(
                    "Gardez cet écran ouvert pendant la lecture d'une vidéo : la TV lit le fichier directement sur le téléphone.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BodyBrush)
            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Control(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(icon, contentDescription = description)
    }
}
