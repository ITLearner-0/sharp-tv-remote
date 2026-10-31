package io.github.itlearner0.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Valeur spéciale : l'utilisateur ne trouve pas sa marque, on essaie tous les jeux de codes. */
private const val ALL_BRANDS = "*"

/**
 * Assistant d'ajout d'une TV par infrarouge : choix de la marque, puis essai
 * des jeux de codes un par un jusqu'à trouver celui auquel la TV répond.
 */
@Composable
fun IrSetupScreen(state: UiState, vm: RemoteViewModel) {
    var brand by rememberSaveable { mutableStateOf(vm.suggestedIrBrand()) }
    var index by rememberSaveable { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = { if (brand != null) brand = null else vm.cancelIrSetup() }) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (brand != null) "  Choisir une autre marque" else "  Retour")
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentSoft, Accent))),
            ) {
                Icon(Icons.Filled.SettingsRemote, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(if (state.irAttach) "Associer l'infrarouge" else "Télécommande infrarouge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (brand == null) "Quelle est la marque de la TV ?"
                else "Pointez le haut du téléphone vers la TV, puis essayez les touches.",
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (!state.irAvailable) {
            IrCard {
                Text(
                    "Ce téléphone n'a pas d'émetteur infrarouge. Cette fonction marche sur les téléphones qui en " +
                        "ont un (beaucoup de Xiaomi, Redmi et Poco, certains Huawei et Honor).",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        val selected = brand
        if (selected == null) {
            BrandPicker(vm.irDatabase.brands) {
                brand = it
                index = 0
            }
        } else {
            val codesets = if (selected == ALL_BRANDS) vm.irDatabase.codesets else vm.irDatabase.forBrand(selected)
            CodesetTester(codesets, index, onIndex = { index = it }, vm, attach = state.irAttach)
        }
    }
}

@Composable
private fun IrCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BodyBrush)
            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun BrandPicker(brands: List<String>, onPick: (String) -> Unit) {
    IrCard {
        for (row in brands.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (b in row) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(KeyColor)
                            .clickable { onPick(b) },
                    ) { Text(b, fontSize = 14.sp, textAlign = TextAlign.Center) }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        OutlinedButton(onClick = { onPick(ALL_BRANDS) }, modifier = Modifier.fillMaxWidth()) {
            Text("Autre marque / je ne sais pas")
        }
    }
}

@Composable
private fun CodesetTester(
    codesets: List<io.github.itlearner0.tvremote.ir.IrCodeset>,
    index: Int,
    onIndex: (Int) -> Unit,
    vm: RemoteViewModel,
    attach: Boolean,
) {
    if (codesets.isEmpty()) return
    if (index >= codesets.size) {
        IrCard {
            Text("Aucun des ${codesets.size} jeux de codes n'a fonctionné.", fontWeight = FontWeight.SemiBold)
            Text(
                "Vérifiez que rien ne masque l'émetteur (en haut du téléphone) et visez le capteur de la TV, " +
                    "à moins de 3 mètres. Vous pouvez recommencer, ou essayer « Autre marque ».",
                color = Muted,
                fontSize = 13.sp,
            )
            FilledTonalButton(onClick = { onIndex(0) }, modifier = Modifier.fillMaxWidth()) { Text("Recommencer") }
        }
        return
    }
    val codeset = codesets[index]
    IrCard {
        Text("Essai ${index + 1} sur ${codesets.size}", color = Muted, fontSize = 12.sp)
        Text(codeset.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        if (!codeset.verified) {
            Text("Codes pas encore confirmés sur une vraie TV : votre essai compte !", color = Muted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TestButton(Icons.Filled.VolumeUp, "Volume +", Modifier.weight(1f), enabled = "VOL_UP" in codeset.keys) {
                vm.sendIr(codeset, "VOL_UP")
            }
            TestButton(Icons.Filled.PowerSettingsNew, "Marche", Modifier.weight(1f), enabled = "POWER" in codeset.keys) {
                vm.sendIr(codeset, "POWER")
            }
        }
        Text("La TV a-t-elle réagi ?", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = { vm.addIrTv(codeset) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (attach) "  Oui, utiliser ces codes" else "  Oui, ajouter cette TV", fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onIndex(index - 1) }, enabled = index > 0, modifier = Modifier.weight(1f)) {
                Text("Précédent")
            }
            OutlinedButton(onClick = { onIndex(index + 1) }, modifier = Modifier.weight(1f)) { Text("Non, suivant") }
        }
    }
}

@Composable
private fun TestButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.height(56.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text("  $label")
    }
}
