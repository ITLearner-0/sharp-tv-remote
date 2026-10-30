package io.github.itlearner0.tvremote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun App(state: UiState, message: String?, vm: RemoteViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            vm.consumeMessage()
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(16.dp)) {
                val showRemote = state.connection == Connection.CONNECTED || state.connection == Connection.OFFLINE
                if (showRemote) {
                    RemoteScreen(state, vm, onSettings = { showSettings = true })
                } else {
                    SetupScreen(state, vm)
                }
            }
        }
    }

    if (showSettings) SettingsDialog(state, vm, onDismiss = { showSettings = false })
}

@Composable
private fun SettingsDialog(state: UiState, vm: RemoteViewModel, onDismiss: () -> Unit) {
    var mac by rememberSaveable { mutableStateOf(state.mac.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmForget by remember { mutableStateOf(false) }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Changer de TV") },
            text = { Text("Oublier cette TV pour en choisir une autre ?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.forgetTv()
                    onDismiss()
                }) { Text("Oublier") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Annuler") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages") },
        text = {
            Column {
                state.host?.let { Text("TV : $it", color = Muted) }
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it; error = null },
                    label = { Text("Adresse MAC (pour allumer la TV)") },
                    placeholder = { Text("A4:B1:C2:D3:E4:F5") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { message -> @Composable { Text(message) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Text(
                    "Détectée automatiquement à la connexion. Pour que l'allumage fonctionne, activez aussi " +
                        "sur la TV « Wake on LAN / Réveil par le réseau » ou « Veille réseau » " +
                        "(Réseau et Internet, ou Système → Alimentation).",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { confirmForget = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Changer de TV")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (vm.setMac(mac)) onDismiss() else error = "Adresse MAC invalide"
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
