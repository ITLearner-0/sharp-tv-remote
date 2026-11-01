package io.github.itlearner0.tvremote.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.itlearner0.tvremote.data.LocalBackup
import io.github.itlearner0.tvremote.data.SavedTv
import io.github.itlearner0.tvremote.protocol.RemoteSession

@Composable
fun App(state: UiState, message: String?, vm: RemoteViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTvs by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            vm.consumeMessage()
            snackbar.showSnackbar(message)
        }
    }

    // Présentation au premier lancement.
    val context = androidx.compose.ui.platform.LocalContext.current
    var onboarded by rememberSaveable { mutableStateOf(onboardingDone(context)) }
    if (!onboarded) {
        OnboardingScreen(onDone = {
            setOnboardingDone(context)
            onboarded = true
        })
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        modifier = Modifier.appBackground(),
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(16.dp)) {
                if (state.showSetup) {
                    SetupScreen(state, vm)
                } else {
                    RemoteScreen(state, vm, onSettings = { showSettings = true }, onSwitchTv = { showTvs = true })
                }
            }
        }
    }

    val proOffer by io.github.itlearner0.tvremote.pro.Pro.offer.collectAsState()
    proOffer?.let { ProDialog(it.feature) }
    if (showSettings && state.current != null) SettingsDialog(state, vm, onDismiss = { showSettings = false })
    if (showTvs) TvListDialog(state, vm, onDismiss = { showTvs = false })
    state.crashReport?.let { CrashReportDialog(it, vm) }
}

/** Détail du dernier plantage, à copier pour le transmettre (rien n'est envoyé automatiquement). */
@Composable
private fun CrashReportDialog(report: String, vm: RemoteViewModel) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = vm::dismissCrashReport,
        title = { Text("Un problème est survenu") },
        text = {
            Column {
                Text(
                    "L'application a rencontré une erreur. Touchez « Copier » puis collez ce texte dans votre " +
                        "message pour qu'elle soit corrigée.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SelectionContainer {
                    Text(
                        report,
                        color = Muted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(report))
                copied = true
            }) { Text(if (copied) "Copié ✓" else "Copier") }
        },
        dismissButton = { TextButton(onClick = vm::dismissCrashReport) { Text("Fermer") } },
    )
}

@Composable
private fun TvListDialog(state: UiState, vm: RemoteViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mes TV") },
        text = {
            Column {
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json"),
                ) { uri -> uri?.let(vm::exportBackup) }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri -> uri?.let(vm::importBackup) }
                for (tv in state.tvs) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            vm.selectTv(tv.id)
                            onDismiss()
                        },
                    ) {
                        RadioButton(selected = tv.id == state.current?.id, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(tv.name)
                            Text(tv.host, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                NotificationSwitch(state, vm)
                Text(
                    "Widget : appui long sur l'écran d'accueil → Widgets → OmniZap. " +
                        "Tuile : réglages rapides → modifier → « TV ». Partage : dans YouTube ou le " +
                        "navigateur, Partager → « Ouvrir sur la TV ».",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Sauvegarde locale",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Les réglages sont aussi copiés automatiquement dans Téléchargements/OmniZap, " +
                        "un fichier conservé si l'application est désinstallée.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    TextButton(onClick = { exportLauncher.launch(LocalBackup.FILE_NAME) }) { Text("Exporter") }
                    TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Restaurer") }
                }
            }
        },
        confirmButton = {
            val context = androidx.compose.ui.platform.LocalContext.current
            TextButton(onClick = {
                // Une TV est gratuite ; les suivantes font partie d'OmniZap Pro.
                if (state.tvs.isEmpty() || io.github.itlearner0.tvremote.pro.Pro.require(context, io.github.itlearner0.tvremote.pro.ProFeature.MULTI_TV)) {
                    vm.addTv()
                }
                onDismiss()
            }) { Text("＋ Ajouter une TV") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun SettingsDialog(state: UiState, vm: RemoteViewModel, onDismiss: () -> Unit) {
    val tv = state.current ?: return
    var name by rememberSaveable(tv.id) { mutableStateOf(tv.name) }
    var host by rememberSaveable(tv.id) { mutableStateOf(tv.host) }
    var mac by rememberSaveable(tv.id, tv.mac) { mutableStateOf(tv.mac.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Supprimer cette TV") },
            text = { Text("Supprimer « ${tv.name} » de la télécommande ? Il faudra refaire l'appairage pour la retrouver.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeCurrentTv()
                    onDismiss()
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Annuler") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages de la TV") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tv.isBt && tv.host.isBlank()) {
                    Text(
                        "Télécommande Bluetooth : " + if (state.btConnected) "connectée" else "non connectée (la TV doit être allumée, à portée)",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = {
                        onDismiss()
                        vm.startBtSetup(attach = true)
                    }) { Text("Associer de nouveau") }
                } else if (tv.host.isBlank()) {
                    val codeset = vm.irDatabase.codeset(tv.irCodeset)
                    Text(
                        "Télécommande infrarouge : ${codeset?.name ?: "jeu de codes inconnu"}.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(
                        onClick = {
                            onDismiss()
                            vm.startIrSetup(attach = true)
                        },
                        enabled = state.irAvailable,
                    ) { Text("Changer les codes") }
                } else {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it; error = null },
                        label = { Text("Adresse IP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it; error = null },
                        label = { Text("Adresse MAC (pour allumer la TV)") },
                        placeholder = { Text("A4:B1:C2:D3:E4:F5") },
                        singleLine = true,
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = vm::detectMac, enabled = !state.detectingMac) {
                        Text(if (state.detectingMac) "Détection en cours…" else "Détecter l'adresse MAC automatiquement")
                    }
                    state.macStatus?.let {
                        Text(
                            it,
                            color = if (it.startsWith("✓")) OnColor else Muted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        "Pour allumer la TV éteinte, elle doit garder le réseau en veille. Sur la TV, activez " +
                            "selon le modèle « Wake on LAN », « Réveil par le réseau », « Veille réseau » ou " +
                            "« Démarrage rapide » (Paramètres → Réseau et Internet, ou Système → Alimentation). " +
                            "Une TV branchée en câble Ethernet se réveille plus sûrement qu'en Wi-Fi.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Astuce : si Menu ou Guide ne fait rien, maintenez la touche appuyée pour choisir " +
                            "la commande adaptée à votre TV.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    IrSection(state, vm, onDismiss)
                    BtSection(state, vm, onDismiss)
                }
                if (tv.host.isNotBlank() && state.connection == Connection.CONNECTED) {
                    val features = state.tv.features
                    val power = features and RemoteSession.FEATURE_POWER != 0
                    Text(
                        "Diagnostic : la TV annonce les fonctions 0x${features.toString(16)} " +
                            "(marche/arrêt ${if (power) "pris en charge" else "non annoncé"}).",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TextButton(onClick = { confirmRemove = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Supprimer cette TV", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = vm.editCurrentTv(name, host, mac)
                if (problem == null) onDismiss() else error = problem
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Télécommande dans les notifications (et sur l'écran verrouillé). */
@Composable
private fun NotificationSwitch(state: UiState, vm: RemoteViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setNotificationEnabled(true)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Télécommande dans les notifications", style = MaterialTheme.typography.titleSmall)
            Text("Marche, volume et lecture, même écran verrouillé", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = state.notificationEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !io.github.itlearner0.tvremote.pro.Pro.require(context, io.github.itlearner0.tvremote.pro.ProFeature.QUICK)) {
                    // Fonction Pro : l'offre s'affiche.
                } else if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.setNotificationEnabled(enabled)
                }
            },
        )
    }
}

/** Bluetooth associé à une TV Wi-Fi : secours quand le réseau ne répond pas, ou mode exclusif. */
@Composable
private fun BtSection(state: UiState, vm: RemoteViewModel, onDismiss: () -> Unit) {
    val tv = state.current ?: return
    Column(Modifier.padding(top = 12.dp)) {
        Text("Bluetooth", style = MaterialTheme.typography.titleSmall)
        Text(
            if (tv.btAddress == null) {
                "Non configuré. Le téléphone peut servir de télécommande Bluetooth quand le Wi-Fi ne répond pas, " +
                    "sans réseau, à une dizaine de mètres."
            } else {
                "Associé (${tv.btAddress})" + if (state.btConnected) " · connecté" else ""
            },
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row {
            TextButton(onClick = {
                onDismiss()
                vm.startBtSetup(attach = true)
            }) { Text(if (tv.btAddress == null) "Configurer le Bluetooth" else "Associer de nouveau") }
            if (tv.btAddress != null) TextButton(onClick = vm::removeBt) { Text("Retirer") }
        }
        if (tv.btAddress != null) {
            val modes = listOf(
                SavedTv.CONTROL_AUTO to "Automatique : Wi-Fi, Bluetooth si la TV ne répond pas",
                SavedTv.CONTROL_NETWORK to "Toujours le Wi-Fi",
                SavedTv.CONTROL_BT to "Toujours le Bluetooth",
            )
            for ((mode, label) in modes) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { vm.setControl(mode) },
                ) {
                    RadioButton(selected = tv.control == mode, onClick = { vm.setControl(mode) })
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Infrarouge associé à une TV Wi-Fi : secours quand le réseau ne répond pas, ou mode exclusif. */
@Composable
private fun IrSection(state: UiState, vm: RemoteViewModel, onDismiss: () -> Unit) {
    val tv = state.current ?: return
    val codeset = vm.irDatabase.codeset(tv.irCodeset)
    Column(Modifier.padding(top = 12.dp)) {
        Text("Infrarouge", style = MaterialTheme.typography.titleSmall)
        Text(
            if (codeset == null) {
                "Non configuré. Utile pour allumer la TV complètement éteinte, ou quand le Wi-Fi ne répond pas" +
                    if (state.irAvailable) "." else " (nécessite un téléphone avec émetteur infrarouge)."
            } else {
                "Codes : ${codeset.name}"
            },
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row {
            TextButton(
                onClick = {
                    onDismiss()
                    vm.startIrSetup(attach = true)
                },
                enabled = state.irAvailable,
            ) { Text(if (codeset == null) "Configurer l'infrarouge" else "Changer les codes") }
            if (codeset != null) TextButton(onClick = vm::removeIr) { Text("Retirer") }
        }
        if (codeset != null) {
            val modes = listOf(
                SavedTv.CONTROL_AUTO to "Automatique : Wi-Fi, infrarouge si la TV ne répond pas",
                SavedTv.CONTROL_NETWORK to "Toujours le Wi-Fi",
                SavedTv.CONTROL_IR to "Toujours l'infrarouge",
            )
            for ((mode, label) in modes) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { vm.setControl(mode) },
                ) {
                    RadioButton(selected = tv.control == mode, onClick = { vm.setControl(mode) })
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
