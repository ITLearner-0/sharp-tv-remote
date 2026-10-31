package io.github.itlearner0.tvremote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itlearner0.tvremote.data.LocalBackup

@Composable
fun SetupScreen(state: UiState, vm: RemoteViewModel) {
    if (state.irSetup) {
        IrSetupScreen(state, vm)
        return
    }
    // Recherche automatique et continue des TV tant que cet écran est affiché.
    DisposableEffect(Unit) {
        vm.startAutoDiscovery()
        onDispose { vm.stopAutoDiscovery() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.tvs.isNotEmpty() && state.connection != Connection.PAIRING) {
            TextButton(onClick = vm::cancelSetup) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Revenir à la télécommande")
            }
        }
        Hero(state)
        when (state.connection) {
            Connection.PAIRING -> PairingStep(vm)
            Connection.CONNECTING -> Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.size(14.dp))
                    Text("Connexion à ${state.pendingHost}…")
                }
            }
            else -> {
                if (state.tvs.isEmpty()) RestoreBackup(vm)
                FoundTvs(state, vm)
                ManualIp(state, vm)
                WakeOffTv(vm)
                InfraredEntry(state, vm)
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun Hero(state: UiState) {
    val pairing = state.connection == Connection.PAIRING
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AccentSoft, Accent))),
        ) {
            Icon(Icons.Filled.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (pairing) "Saisissez le code" else if (state.tvs.isEmpty()) "Ajoutons votre TV" else "Ajouter une TV",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (pairing) "Il s'affiche maintenant sur l'écran de la TV."
            else "Allumez la TV et restez sur le même Wi-Fi : elle apparaîtra ci-dessous.",
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BodyBrush)
            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun FoundTvs(state: UiState, vm: RemoteViewModel) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
            Text(
                "  TV trouvées sur le réseau",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (state.discovering) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        if (state.discovered.isEmpty()) {
            Text(
                "Recherche en cours… Si rien n'apparaît, vérifiez que la TV est allumée et sur le même Wi-Fi que ce téléphone.",
                color = Muted,
                fontSize = 13.sp,
            )
        }
        for (tv in state.discovered) {
            val known = state.tvs.any { it.host == tv.host }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KeyColor)
                    .clickable { vm.connect(tv.host, tv.name) }
                    .padding(14.dp),
            ) {
                Icon(Icons.Filled.Tv, contentDescription = null, tint = Color.White)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(tv.name, fontWeight = FontWeight.SemiBold)
                    Text(if (known) "${tv.host} · déjà ajoutée" else tv.host, color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Ajouter", tint = Muted)
            }
        }
    }
}

@Composable
private fun ManualIp(state: UiState, vm: RemoteViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf(state.pendingHost.orEmpty()) }
    Card {
        Text(
            (if (open) "▾ " else "▸ ") + "Saisir l'adresse IP de la TV",
            color = Muted,
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
        )
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Adresse IP") },
                    placeholder = { Text("192.168.1.42") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.connect(host) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.connect(host) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Se connecter", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Sur la TV : Paramètres → Réseau et Internet → votre Wi-Fi.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** TV complètement éteinte : invisible sur le réseau, on la réveille par son adresse MAC. */
@Composable
private fun WakeOffTv(vm: RemoteViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    var mac by rememberSaveable { mutableStateOf("") }
    Card {
        Text(
            (if (open) "▾ " else "▸ ") + "La TV est éteinte ?",
            color = Muted,
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
        )
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Une TV éteinte n'est pas visible sur le réseau. Si elle accepte le réveil par le réseau, " +
                        "saisissez son adresse MAC : elle s'allumera puis apparaîtra dans la liste ci-dessus.",
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("Adresse MAC de la TV") },
                    placeholder = { Text("A4:B1:C2:D3:E4:F5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { vm.wakeByMac(mac) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Allumer la TV")
                }
                Text(
                    "Où trouver l'adresse MAC : sur la TV (Paramètres → À propos → État, ou Réseau), dans la " +
                        "liste des appareils de votre box Internet, ou sur l'étiquette au dos de la TV.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** Après une réinstallation : restaurer les TV depuis la sauvegarde locale. */
@Composable
private fun RestoreBackup(vm: RemoteViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }
    Card {
        Text("Vous aviez déjà utilisé l'application ?", fontWeight = FontWeight.SemiBold)
        Text(
            "Restaurez vos TV depuis la sauvegarde : Téléchargements → OmniZap → ${LocalBackup.FILE_NAME} " +
                "(sauvegarde plus ancienne : Téléchargements → Télécommande TV).",
            color = Muted,
            fontSize = 13.sp,
        )
        OutlinedButton(
            onClick = { launcher.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restaurer une sauvegarde") }
    }
}

/** TV non connectée ou ancienne : télécommande infrarouge (si le téléphone a un émetteur). */
@Composable
private fun InfraredEntry(state: UiState, vm: RemoteViewModel) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SettingsRemote, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
            Text("  TV non connectée ou ancienne ?", fontWeight = FontWeight.SemiBold)
        }
        Text(
            if (state.irAvailable) "Ce téléphone a un émetteur infrarouge : il peut piloter n'importe quelle TV, " +
                "comme une télécommande classique."
            else "Il faudrait un téléphone avec émetteur infrarouge (beaucoup de Xiaomi, Redmi et Poco, certains Huawei et Honor).",
            color = Muted,
            fontSize = 13.sp,
        )
        OutlinedButton(
            onClick = vm::startIrSetup,
            enabled = state.irAvailable,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Utiliser l'infrarouge") }
    }
}

@Composable
private fun PairingStep(vm: RemoteViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    Card {
        OutlinedTextField(
            value = code,
            onValueChange = { value -> code = value.uppercase().filter { it.isLetterOrDigit() }.take(6) },
            singleLine = true,
            placeholder = { Text("A1B2C3", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { vm.submitCode(code) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.submitCode(code) },
            enabled = code.length == 6,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Valider", fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = vm::cancelSetup, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
    }
}
