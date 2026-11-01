package io.github.itlearner0.tvremote.ui

import io.github.itlearner0.tvremote.i18n.tr

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
        tr("Menu"),
        Keys.MENU,
        listOf(
            tr("Menu standard Android") to Keys.MENU,
            tr("Menu contextuel TV") to 257, // KEYCODE_TV_MEDIA_CONTEXT_MENU
            tr("Menu des contenus TV") to 256, // KEYCODE_TV_CONTENTS_MENU
            tr("Menu principal (média)") to 226, // KEYCODE_MEDIA_TOP_MENU
            tr("Réglages rapides") to Keys.SETTINGS,
            tr("Toutes les applications") to 284, // KEYCODE_ALL_APPS
        ),
    ),
    GUIDE(
        tr("Guide"),
        Keys.GUIDE,
        listOf(
            tr("Guide des programmes") to Keys.GUIDE,
            tr("Menu des contenus TV") to 256, // KEYCODE_TV_CONTENTS_MENU
            tr("Télévision en direct") to 170, // KEYCODE_TV
            tr("Enregistrements (DVR)") to 173, // KEYCODE_DVR
            tr("Programmation") to 258, // KEYCODE_TV_TIMER_PROGRAMMING
            tr("Favoris") to 174, // KEYCODE_BOOKMARK
        ),
    ),
}

@Composable
fun CustomKeyDialog(key: CustomKey, current: Int, vm: RemoteViewModel, onDismiss: () -> Unit) {
    var selected by remember { mutableIntStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Touche « {1} »", key.label)) },
        text = {
            Column {
                Text(
                    tr("Les fabricants n'utilisent pas tous la même commande. Touchez « Essayer » pour tester ") +
                        tr("chaque possibilité sur la TV, puis gardez celle qui fonctionne."),
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
                        }) { Text(tr("Essayer")) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setCustomKey(key, selected)
                onDismiss()
            }) { Text(tr("Garder")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler")) } },
    )
}
