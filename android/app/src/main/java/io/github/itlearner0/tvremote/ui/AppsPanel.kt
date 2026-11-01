package io.github.itlearner0.tvremote.ui

import io.github.itlearner0.tvremote.i18n.tr

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itlearner0.tvremote.data.TvApp

@Composable
fun AppsPanel(state: UiState, vm: RemoteViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    // Liste relue automatiquement à l'ouverture de l'onglet si elle est ancienne.
    LaunchedEffect(state.connection, state.current?.id) { vm.refreshAppsIfStale() }
    val filtered = state.apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    val (tvApps, otherApps) = filtered.partition { it.installed?.tvApp != false }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.apps.size > 9) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Rechercher une application")) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (otherApps.isNotEmpty()) SectionTitle(tr("Applications TV"))
        AppGrid(tvApps, state, vm)
        if (otherApps.isNotEmpty()) {
            SectionTitle(tr("Autres applications"))
            AppGrid(otherApps, state, vm)
        }
        if (filtered.isEmpty()) Text(tr("Aucune application ne correspond."), color = Muted, fontSize = 13.sp)

        OutlinedButton(
            onClick = { vm.scanApps() },
            enabled = !state.scanningApps,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (state.scanningApps) tr("  Lecture en cours…") else tr("  Lister toutes les applications de la TV"),
                fontSize = 14.sp,
            )
        }
        if (state.scanningApps) LinearProgressIndicator(Modifier.fillMaxWidth())
        val hint = state.appsMessage ?: if (state.appsComplete) null else
            tr("Seules les applications courantes et celles déjà ouvertes sur la TV s'affichent. ") +
                tr("Pour toutes les voir, activez le « Débogage USB » sur la TV, puis touchez le bouton ci-dessus.")
        hint?.let { Text(it, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

@Composable
private fun AppGrid(apps: List<TvApp>, state: UiState, vm: RemoteViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in apps.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (app in row) {
                    val icon = remember(app.packageName, state.appsVersion, state.current?.id) { vm.appIcon(app.packageName) }
                    AppTile(app, icon, app.packageName == state.tv.currentApp, Modifier.weight(1f)) { vm.launch(app) }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppTile(app: TvApp, icon: ImageBitmap?, running: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (start, end) = brandColors(app.packageName)
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (icon != null) Brush.linearGradient(listOf(KeyColor, KeyColor)) else Brush.linearGradient(listOf(start, end)))
                .then(if (running) Modifier.border(2.dp, OnColor, RoundedCornerShape(18.dp)) else Modifier),
        ) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(8.dp))
            } else {
                Text(monogram(app), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            if (running) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(9.dp).clip(CircleShape).background(OnColor),
                )
            }
        }
        Text(
            app.label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (running) OnColor else Color.White,
        )
    }
}

/** Lettres affichées sur la tuile : « N » pour Netflix, « PV » pour Prime Video… */
private fun monogram(app: TvApp): String {
    MONOGRAMS[app.packageName]?.let { return it }
    val words = app.label.split(' ', '-', '.').filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
        else -> app.label.take(1).uppercase()
    }
}

private fun brandColors(packageName: String): Pair<Color, Color> {
    BRANDS[packageName]?.let { return it }
    val hue = (packageName.sumOf { it.code } % 360).toFloat()
    return Color.hsl(hue, 0.55f, 0.46f) to Color.hsl((hue + 35f) % 360f, 0.6f, 0.28f)
}

private val MONOGRAMS = mapOf(
    "com.netflix.ninja" to "N",
    "com.google.android.youtube.tv" to "▶",
    "com.google.android.youtube.tvkids" to "▶",
    "com.google.android.youtube.tvmusic" to "♪",
    "com.amazon.amazonvideo.livingroom" to "prime",
    "com.disney.disneyplus" to tr("D+"),
    "com.spotify.tv.android" to "♫",
    "com.android.vending" to "▷",
)

private val BRANDS = mapOf(
    "com.netflix.ninja" to (Color(0xFFE50914) to Color(0xFF831010)),
    "com.google.android.youtube.tv" to (Color(0xFFFF0000) to Color(0xFFB00000)),
    "com.google.android.youtube.tvkids" to (Color(0xFFFF4E45) to Color(0xFFB71C1C)),
    "com.google.android.youtube.tvmusic" to (Color(0xFFFF0033) to Color(0xFF3A0010)),
    "com.amazon.amazonvideo.livingroom" to (Color(0xFF00A8E1) to Color(0xFF0F2E4A)),
    "com.disney.disneyplus" to (Color(0xFF113CCF) to Color(0xFF0A1440)),
    "com.spotify.tv.android" to (Color(0xFF1ED760) to Color(0xFF0D5C2B)),
    "deezer.android.tv" to (Color(0xFFA238FF) to Color(0xFF3B0F6B)),
    "com.canal.android.canal" to (Color(0xFF222222) to Color(0xFF000000)),
    "fr.francetv.pluzz" to (Color(0xFF1B2CC1) to Color(0xFF0B0F4A)),
    "tv.arte.plus7" to (Color(0xFFFA481C) to Color(0xFF7A1E08)),
    "tv.molotov.app" to (Color(0xFF2D2D3A) to Color(0xFF0E0E14)),
    "com.plexapp.android" to (Color(0xFFE5A00D) to Color(0xFF282A2D)),
    "org.xbmc.kodi" to (Color(0xFF17B2E7) to Color(0xFF0B3A52)),
    "org.videolan.vlc" to (Color(0xFFFF8800) to Color(0xFF8A3B00)),
    "tv.twitch.android.app" to (Color(0xFF9146FF) to Color(0xFF3A1A73)),
    "com.android.vending" to (Color(0xFF01875F) to Color(0xFF0A3D2C)),
    "com.android.tv.settings" to (Color(0xFF5F6368) to Color(0xFF2B2E33)),
)
