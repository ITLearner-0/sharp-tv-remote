package io.github.itlearner0.tvremote.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Page de présentation : grande icône, titre, texte et trois atouts. */
private data class IntroPage(
    val icon: ImageVector,
    val title: String,
    val text: String,
    val points: List<Pair<ImageVector, String>>,
)

private val PAGES = listOf(
    IntroPage(
        Icons.Filled.SettingsRemote,
        "OmniZap",
        "La télécommande universelle de votre TV, dans votre téléphone.",
        listOf(
            Icons.Filled.Shield to "Sans publicité, sans abonnement",
            Icons.Filled.PowerSettingsNew to "Allume la TV, même éteinte",
            Icons.Filled.Mic to "« Lance Netflix » à la voix",
        ),
    ),
    IntroPage(
        Icons.Filled.Wifi,
        "Toutes vos TV",
        "Wi-Fi pour les TV Android et Google TV, infrarouge et Bluetooth pour les autres, même hors réseau.",
        listOf(
            Icons.Filled.Wifi to "Wi-Fi : détection automatique",
            Icons.Filled.SettingsRemote to "Infrarouge : toutes les marques",
            Icons.Filled.Bluetooth to "Bluetooth : sans réseau",
        ),
    ),
    IntroPage(
        Icons.Filled.Cast,
        "Bien plus qu'une télécommande",
        "Le téléphone et la TV travaillent ensemble.",
        listOf(
            Icons.Filled.Apps to "Les applications de la TV, d'un geste",
            Icons.Filled.Cast to "Photos, vidéos et écran sur la TV",
            Icons.Filled.Tv to "L'écran de la TV sur le téléphone",
        ),
    ),
    IntroPage(
        Icons.Filled.Lock,
        "Vos données restent chez vous",
        "Aucun compte, aucune collecte. Vos réglages sont sauvegardés sur le téléphone, jamais ailleurs.",
        listOf(
            Icons.Filled.Lock to "Aucune donnée envoyée",
            Icons.Filled.Shield to "Sauvegarde locale",
        ),
    ),
)

private const val PREFS = "ui"
private const val KEY_DONE = "onboarding_done"

fun onboardingDone(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

fun setOnboardingDone(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
}

/** Présentation au premier lancement. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    Box(Modifier.fillMaxSize().appBackground().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onDone) { Text("Passer", color = Muted) }
                else Spacer(Modifier.height(48.dp))
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                Page(PAGES[index], active = index == pager.currentPage)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 20.dp)) {
                repeat(PAGES.size) { i ->
                    val selected = i == pager.currentPage
                    val width by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
                    val color by animateColorAsState(if (selected) AccentSoft else BorderColor, label = "dot-color")
                    Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
                }
            }
            GradientButton(if (last) "Commencer" else "Continuer") {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Page(page: IntroPage, active: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RadarIcon(page.icon, active = active, diameter = 96.dp)
        Text(
            page.title,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            page.text,
            color = Muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            for ((icon, label) in page.points) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BodyBrush)
                        .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(KeyColor),
                    ) {
                        Icon(icon, contentDescription = null, tint = AccentSoft, modifier = Modifier.size(20.dp))
                    }
                    Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp))
                }
            }
        }
    }
}
