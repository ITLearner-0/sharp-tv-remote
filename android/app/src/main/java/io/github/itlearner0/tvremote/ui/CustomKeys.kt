package io.github.itlearner0.tvremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itlearner0.tvremote.protocol.Keys

/**
 * Touches dont le code varie selon les fabricants. Un appui long ouvre la
 * liste des codes possibles ; celui choisi est mémorisé pour chaque TV.
 */
enum class CustomKey(val label: String, val default: Int, val choices: List<Pair<String, Int>>) {
    MENU(
        "Menu",
        Keys.MENU,
        listOf(
            "Menu standard Android" to Keys.MENU,
            "Menu contextuel TV" to 257, // KEYCODE_TV_MEDIA_CONTEXT_MENU
            "Menu des contenus TV" to 256, // KEYCODE_TV_CONTENTS_MENU
            "Menu principal (média)" to 226, // KEYCODE_MEDIA_TOP_MENU
            "Réglages rapides" to Keys.SETTINGS,
            "Toutes les applications" to 284, // KEYCODE_ALL_APPS
        ),
    ),
    GUIDE(
        "Guide",
        Keys.GUIDE,
        listOf(
            "Guide des programmes" to Keys.GUIDE,
            "Menu des contenus TV" to 256, // KEYCODE_TV_CONTENTS_MENU
            "Télévision en direct" to 170, // KEYCODE_TV
            "Enregistrements (DVR)" to 173, // KEYCODE_DVR
            "Programmation" to 258, // KEYCODE_TV_TIMER_PROGRAMMING
            "Favoris" to 174, // KEYCODE_BOOKMARK
        ),
    ),
}

@Composable
fun CustomKeyDialog(key: CustomKey, current: Int, vm: RemoteViewModel, onDismiss: () -> Unit) {
    var selected by remember { mutableIntStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Touche « ${key.label} »") },
        text = {
            Column {
                Text(
                    "Les fabricants n'utilisent pas tous la même commande. Touchez « Essayer » pour tester " +
                        "chaque possibilité sur la TV, puis gardez celle qui fonctionne.",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                for ((label, code) in key.choices) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selected = code },
                    ) {
                        RadioButton(selected = selected == code, onClick = { selected = code })
                        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        TextButton(onClick = {
                            selected = code
                            vm.key(code)
                        }) { Text("Essayer") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setCustomKey(key, selected)
                onDismiss()
            }) { Text("Garder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
