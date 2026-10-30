package io.github.itlearner0.tvremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen(state: UiState, vm: RemoteViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Télécommande TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Pour les TV Android TV et Google TV (Sharp, Sony, Philips, TCL, Xiaomi…). " +
                "La TV et ce téléphone doivent être sur le même Wi-Fi.",
            color = Muted,
        )
        when (state.connection) {
            Connection.PAIRING -> PairingStep(vm)
            Connection.CONNECTING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
                Text("Connexion à ${state.host}…")
            }
            else -> ChooseTvStep(state, vm)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ChooseTvStep(state: UiState, vm: RemoteViewModel) {
    var host by rememberSaveable { mutableStateOf(state.host.orEmpty()) }
    FilledTonalButton(onClick = vm::discover, enabled = !state.discovering, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.discovering) "Recherche en cours…" else "🔍  Rechercher ma TV")
    }
    for (tv in state.discovered) {
        FilledTonalButton(onClick = { vm.connect(tv.host) }, modifier = Modifier.fillMaxWidth()) {
            Text("📺  ${tv.name} (${tv.host})")
        }
    }
    if (!state.discovering && state.discovered.isEmpty()) {
        Text("…ou saisissez l'adresse IP de la TV", color = Muted)
    }
    OutlinedTextField(
        value = host,
        onValueChange = { host = it },
        label = { Text("Adresse IP de la TV") },
        placeholder = { Text("192.168.1.42") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { vm.connect(host) }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { vm.connect(host) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Se connecter", fontWeight = FontWeight.SemiBold)
    }
    Text(
        "L'adresse IP est visible sur la TV : Paramètres → Réseau et Internet → votre Wi-Fi.",
        color = Muted,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PairingStep(vm: RemoteViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    Text("Un code à 6 caractères s'affiche sur la TV. Saisissez-le :")
    OutlinedTextField(
        value = code,
        onValueChange = { value -> code = value.uppercase().filter { it.isLetterOrDigit() }.take(6) },
        singleLine = true,
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
    TextButton(onClick = vm::cancelPairing, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Annuler")
    }
}
