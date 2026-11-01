package io.github.itlearner0.tvremote.ui

import io.github.itlearner0.tvremote.i18n.tr

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itlearner0.tvremote.bt.BtRemote

/** Appareil Bluetooth proposé dans l'assistant. */
private data class BtCandidate(val address: String, val name: String, val bonded: Boolean)

/**
 * Assistant Bluetooth : le téléphone devient une télécommande Bluetooth de la TV
 * (comme un clavier). Association une fois, depuis la TV ou depuis le téléphone.
 */
@SuppressLint("MissingPermission")
@Composable
fun BtSetupScreen(state: UiState, vm: RemoteViewModel) {
    val context = LocalContext.current
    val bt = state.bt
    var askedPermissions by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        BtRemote.init(context)
    }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { BtRemote.init(context) }
    val visible = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    LaunchedEffect(Unit) {
        BtRemote.init(context)
        if (!BtRemote.permitted(context) && !askedPermissions) {
            askedPermissions = true
            permissions.launch(BtRemote.permissions)
        }
    }
    // Mode basse consommation (celui des télécommandes des TV Android) lancé d'office.
    LaunchedEffect(bt.permitted, bt.enabled) {
        if (bt.permitted && bt.enabled) BtRemote.startBle(context)
    }

    val found = remember { mutableStateListOf<BtCandidate>() }
    var scanning by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<String?>(null) }
    // Recherche des appareils proches, et fin d'association : connexion aussitôt.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = device(intent) ?: return
                        if (found.none { it.address == d.address }) {
                            found += BtCandidate(d.address, d.name ?: d.address, d.bondState == BluetoothDevice.BOND_BONDED)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val d = device(intent) ?: return
                        if (d.bondState == BluetoothDevice.BOND_BONDED && d.address == chosen) BtRemote.connect(d.address)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        // Diffusions du système Bluetooth : le récepteur doit être « exporté ».
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { adapter(context)?.cancelDiscovery() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = vm::cancelBtSetup) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(tr("  Retour"))
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AccentSoft, Accent))),
            ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (state.btAttach) tr("Associer le Bluetooth") else tr("Télécommande Bluetooth"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                tr("Le téléphone se présente à la TV comme une télécommande et un clavier Bluetooth : ") +
                    tr("sans réseau, à une dizaine de mètres. (Version {1})", io.github.itlearner0.tvremote.BuildConfig.VERSION_NAME),
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        when {
            !bt.supported -> BtCard {
                Text(
                    bt.message ?: tr("Ce téléphone ne permet pas le mode télécommande Bluetooth."),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            !bt.permitted -> BtCard {
                Text(tr("Autorisez OmniZap à utiliser le Bluetooth (« Appareils à proximité »)."))
                Button(onClick = { permissions.launch(BtRemote.permissions) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Autoriser"))
                }
            }
            !bt.enabled -> BtCard {
                Text(tr("Le Bluetooth du téléphone est désactivé."))
                Button(
                    onClick = { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(tr("Activer le Bluetooth")) }
            }
            else -> {
                val connected = bt.connectedAddress
                if (connected != null) {
                    BtCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.BluetoothConnected, contentDescription = null, tint = OnColor)
                            Text(tr("  Connecté à {1}", bt.connectedName ?: connected), fontWeight = FontWeight.SemiBold)
                        }
                        Text(tr("Essayez : la touche Accueil ci-dessous doit agir sur la TV."), color = Muted, fontSize = 13.sp)
                        OutlinedButton(onClick = { BtRemote.key(io.github.itlearner0.tvremote.protocol.Keys.HOME) }, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Essayer : Accueil"))
                        }
                        Button(
                            onClick = { vm.addBtTv(connected, bt.connectedName) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) { Text(if (state.btAttach) tr("Associer à cette TV") else tr("Utiliser cette TV")) }
                    }
                }
                BtCard {
                    Text(tr("Télécommande basse consommation (TV Android, Google TV)"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Le protocole des télécommandes d'origine des TV Android.\n") +
                            tr("1. Touchez le bouton ci-dessous : le téléphone s'annonce comme télécommande.\n") +
                            tr("2. Sur la TV : Paramètres → Télécommandes et accessoires → Ajouter un accessoire, ") +
                            tr("puis choisissez le téléphone (son nom Bluetooth) et acceptez l'association.\n") +
                            tr("3. « Connecté à … » s'affiche ci-dessus : essayez Accueil."),
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    Text(
                        tr("Le téléphone s'annonce déjà comme télécommande dès l'ouverture de cet écran (lignes « BLE : » du journal)."),
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    OutlinedButton(onClick = { BtRemote.startBle(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Relancer l'annonce (BLE)"))
                    }
                }
                // Bluetooth classique : replié par défaut (les TV Android le refusent souvent).
                var showClassic by remember { mutableStateOf(false) }
                TextButton(onClick = { showClassic = !showClassic }) {
                    Text(if (showClassic) tr("Masquer le Bluetooth classique") else tr("Bluetooth classique (autres TV, boîtiers, ordinateurs)…"))
                }
                if (showClassic) {
                    OutlinedButton(onClick = { BtRemote.useClassic(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Passer en Bluetooth classique (arrête la basse consommation)"))
                    }
                BtCard {
                    Text(tr("Bluetooth classique : depuis le téléphone"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("1. Sur la TV, ouvrez Paramètres → Télécommandes et accessoires → Ajouter un accessoire ") +
                            tr("(ou le menu Bluetooth) : la TV devient visible.\n") +
                            tr("2. Ici, touchez « Rechercher la TV », puis le nom de la TV.\n") +
                            tr("3. Acceptez l'association sur la TV et sur le téléphone : OmniZap se connecte."),
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    val bonded = BtRemote.bondedDevices().map { BtCandidate(it.address, it.name ?: it.address, true) }
                    if (bonded.isNotEmpty()) {
                        Text(tr("Appareils déjà associés (touchez la TV pour vous connecter) :"), color = Muted, fontSize = 13.sp)
                        for (d in bonded) DeviceRow(d, connecting = bt.connecting && chosen == d.address) {
                            chosen = d.address
                            BtRemote.connect(d.address)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            found.clear()
                            scanning = adapter(context)?.startDiscovery() == true
                        },
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (scanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(if (scanning) tr("  Recherche…") else tr("Rechercher la TV"))
                    }
                    for (d in found.filter { c -> bonded.none { it.address == c.address } }) {
                        DeviceRow(d, connecting = chosen == d.address && !bt.connecting && bt.connectedAddress == null) {
                            chosen = d.address
                            adapter(context)?.cancelDiscovery()
                            val device = runCatching { adapter(context)?.getRemoteDevice(d.address) }.getOrNull()
                            if (device?.bondState == BluetoothDevice.BOND_BONDED) BtRemote.connect(d.address) else device?.createBond()
                        }
                    }
                }
                BtCard {
                    Text(tr("Autre méthode : depuis la TV"), fontWeight = FontWeight.SemiBold)
                    Text(
                        tr("Pour les TV qui listent les téléphones (Samsung, LG, boîtiers…). Les TV Android et Google TV ") +
                            tr("n'affichent souvent que les claviers et manettes : utilisez alors la méthode ci-dessus."),
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    OutlinedButton(
                        onClick = {
                            visible.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Rendre le téléphone visible (2 min)")) }
                    Text(
                        tr("Quand la TV s'est associée, elle apparaît ci-dessus comme « Connecté »."),
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    if (bt.bleActive) tr("● Téléphone visible comme télécommande basse consommation")
                    else if (bt.registered) tr("● Téléphone prêt comme télécommande Bluetooth")
                    else tr("○ Préparation de la télécommande Bluetooth du téléphone…"),
                    color = if (bt.registered || bt.bleActive) OnColor else Muted,
                    fontSize = 12.sp,
                )
                }
                bt.message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if ((!bt.registered && !bt.bleActive) || bt.message != null) {
                    OutlinedButton(onClick = { BtRemote.reset(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Réessayer"))
                    }
                }
                if (bt.log.isNotEmpty()) {
                    Text(tr("Journal Bluetooth"), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Muted)
                    for (line in bt.log) Text(line, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BtCandidate, connecting: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KeyColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(device.name)
            Text(device.address, color = Muted, fontSize = 11.sp)
        }
        if (connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun BtCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BodyBrush)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

private fun adapter(context: Context): BluetoothAdapter? =
    context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter

@Suppress("DEPRECATION")
private fun device(intent: Intent): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
