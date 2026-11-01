package io.github.itlearner0.tvremote.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.itlearner0.tvremote.pro.Pro
import io.github.itlearner0.tvremote.pro.ProFeature

/** Offre OmniZap Pro : achat unique, sans abonnement ni publicité. */
@Composable
fun ProDialog(feature: ProFeature?) {
    val context = LocalContext.current
    val pro by Pro.state.collectAsState()
    val price = pro.price ?: Pro.LAUNCH_PRICE
    Dialog(onDismissRequest = Pro::dismissOffer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(32.dp))
                .appBackground()
                .border(1.dp, BorderColor, RoundedCornerShape(32.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RadarIcon(Icons.Filled.WorkspacePremium, active = false, diameter = 72.dp)
            Text("OmniZap Pro", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                when {
                    pro.purchased -> "Merci ! Toutes les fonctions sont débloquées, pour toujours."
                    pro.trialActive -> "Essai gratuit : encore ${pro.trialDaysLeft} jour(s) avec toutes les fonctions."
                    feature != null -> "« ${feature.label} » fait partie d'OmniZap Pro. Votre essai de ${Pro.TRIAL_DAYS} jours est terminé."
                    else -> "Votre essai de ${Pro.TRIAL_DAYS} jours est terminé."
                },
                color = Muted,
                textAlign = TextAlign.Center,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (f in ProFeature.entries) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (f == feature) AccentSoft else OnColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "  ${f.label}",
                            color = Color.White,
                            fontWeight = if (f == feature) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            if (!pro.purchased) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BodyBrush)
                        .border(2.dp, Accent.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                ) {
                    Text("Accès à vie · $price", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Paiement unique. Pas d'abonnement, pas de publicité.", color = Muted, fontSize = 13.sp)
                    if (pro.price == null) Text("Prix de lancement", color = AccentSoft, fontSize = 12.sp)
                }
                GradientButton("Débloquer OmniZap Pro", enabled = !pro.busy) {
                    (context as? Activity)?.let(Pro::buy)
                }
                Row {
                    TextButton(onClick = { Pro.restore(context) }) { Text("Restaurer mon achat") }
                    if (Pro.testUnlockAvailable) {
                        TextButton(onClick = { Pro.testUnlock(context) }) { Text("Débloquer (test)") }
                    }
                }
            }
            Text(
                "La télécommande reste gratuite : toutes les touches, le pavé tactile, le clavier, l'infrarouge et l'allumage de la TV.",
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = Pro::dismissOffer) { Text(if (pro.unlocked) "Fermer" else "Plus tard") }
        }
    }
}

/** Remplace un panneau Pro verrouillé (après l'essai). */
@Composable
fun ProLock(feature: ProFeature) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(KeyColor),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = AccentSoft)
        }
        Text(feature.label, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text("Fonction OmniZap Pro", color = Muted, fontSize = 13.sp)
        GradientButton("Découvrir OmniZap Pro") { Pro.showOffer(feature) }
    }
}

/** Badge sous l'en-tête : jours d'essai restants, ou invitation à passer à Pro. */
@Composable
fun ProBadge() {
    val pro by Pro.state.collectAsState()
    if (pro.purchased) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (pro.trialActive) KeyColor else Color.Transparent)
            .then(if (pro.trialActive) Modifier else Modifier.background(AccentBrush))
            .clickable { Pro.showOffer(null) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = if (pro.trialActive) AccentSoft else Color.White, modifier = Modifier.size(18.dp))
        Text(
            if (pro.trialActive) "  Essai Pro : ${pro.trialDaysLeft} jour(s) restant(s)" else "  Passer à OmniZap Pro",
            color = Color.White,
            fontSize = 13.sp,
        )
    }
}
