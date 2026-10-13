package app.simple.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simple.launcher.R
import app.simple.launcher.apps.AppEntry
import app.simple.launcher.apps.AppInfo
import app.simple.launcher.config.HomeConfig
import app.simple.launcher.config.TileAction
import app.simple.launcher.config.TileConfig
import app.simple.launcher.config.TileEditing
import app.simple.launcher.config.UiConfigParser

/**
 * "Rubriques de l'accueil", for the family: what Jean-Pierre sees on the home screen. Move, remove, add a built-in rubrique or any app
 * installed on the phone (WhatsApp, a video app…). The last rubrique can never be removed, and there are at most
 * [UiConfigParser.MAX_TILES].
 */
@Composable
fun HomeTilesEditor(
    tiles: List<TileConfig>,
    appInfo: (String) -> AppInfo?,
    loadApps: () -> List<AppEntry>,
    onChange: (List<TileConfig>) -> Unit,
    onReset: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.tiles_title), fontSize = 28.sp, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.tiles_help, TileEditing.MAX), fontSize = 24.sp)
        tiles.forEachIndexed { index, tile ->
            val app = tile.app?.let(appInfo)
            val name = (if (tile.action == TileAction.APP) app?.label else tile.label) ?: stringResource(tile.action.defaultLabel)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val picture = app?.icon
                        if (picture != null) Image(picture, contentDescription = null, modifier = Modifier.size(48.dp))
                        else Icon(tile.icon.vector(), contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("${index + 1}. $name", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    if (tile.action == TileAction.APP && app == null) Text(stringResource(R.string.tiles_app_missing), fontSize = 24.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        val up = stringResource(R.string.tiles_up, name)
                        val down = stringResource(R.string.tiles_down, name)
                        MoveKey(Icons.Filled.ArrowUpward, stringResource(R.string.tiles_earlier), up, index > 0, Modifier.weight(1f)) { onChange(TileEditing.move(tiles, index, -1)) }
                        MoveKey(Icons.Filled.ArrowDownward, stringResource(R.string.tiles_later), down, index < tiles.lastIndex, Modifier.weight(1f)) { onChange(TileEditing.move(tiles, index, +1)) }
                        ConfirmRemove(
                            label = stringResource(R.string.tiles_remove), confirm = stringResource(R.string.tiles_remove_confirm),
                            enabled = tiles.size > 1, modifier = Modifier.weight(1f),
                        ) { onChange(TileEditing.remove(tiles, index)) }
                    }
                }
            }
        }
        if (!TileEditing.canAdd(tiles)) {
            Text(stringResource(R.string.tiles_full, TileEditing.MAX), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(stringResource(R.string.tiles_add_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TileEditing.missingBuiltIns(tiles).forEach { action ->
                OutlinedButton(onClick = { onChange(TileEditing.addBuiltIn(tiles, action)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tiles_add_named, stringResource(action.defaultLabel)), fontSize = 24.sp)
                }
            }
            Button(onClick = { picking = !picking }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (picking) R.string.tiles_close_apps else R.string.tiles_add_app), fontSize = 24.sp)
            }
            if (picking) AppPicker(loadApps, appInfo, tiles.mapNotNull { it.app }.toSet()) { pkg -> onChange(TileEditing.addApp(tiles, pkg)); picking = false }
        }
        ConfirmRemove(
            label = stringResource(R.string.tiles_reset, HomeConfig.Default.tiles.size), confirm = stringResource(R.string.tiles_reset_confirm),
            enabled = true, modifier = Modifier.fillMaxWidth(), onConfirmed = onReset,
        )
    }
}

/** The apps of the phone with their icons, found by typing the start of their name. */
@Composable
private fun AppPicker(loadApps: () -> List<AppEntry>, appInfo: (String) -> AppInfo?, already: Set<String>, onPick: (String) -> Unit) {
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = loadApps() }
    OutlinedTextField(
        value = query, onValueChange = { query = it },
        label = { Text(stringResource(R.string.tiles_search_apps), fontSize = 24.sp) },
        textStyle = TextStyle(fontSize = 24.sp), singleLine = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    )
    val list = apps
    if (list == null) {
        Text(stringResource(R.string.tiles_loading_apps), fontSize = 24.sp)
        return
    }
    val shown = list.filter { it.packageName !in already && it.label.contains(query.trim(), ignoreCase = true) }.take(60)
    if (shown.isEmpty()) Text(stringResource(R.string.tiles_no_app), fontSize = 24.sp)
    if (shown.size == 60) Text(stringResource(R.string.tiles_type_more), fontSize = 24.sp)
    shown.forEach { entry ->
        val picture = appInfo(entry.packageName)?.icon
        OutlinedButton(onClick = { onPick(entry.packageName) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            if (picture != null) Image(picture, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(entry.label, fontSize = 24.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

/** Two touches for anything that removes: the first only asks, shows a different (filled) button, and gives up by itself after a few seconds. */
@Composable
private fun ConfirmRemove(label: String, confirm: String, enabled: Boolean, modifier: Modifier, onConfirmed: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            kotlinx.coroutines.delay(4_000)
            armed = false
        }
    }
    val click = { if (armed) { armed = false; onConfirmed() } else armed = true }
    if (armed) Button(onClick = click, enabled = enabled, modifier = modifier.heightIn(min = 64.dp)) { Text(confirm, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
    else OutlinedButton(onClick = click, enabled = enabled, modifier = modifier.heightIn(min = 64.dp)) { Text(label, fontSize = 24.sp) }
}

/** Up / down with words: "Avant" and "Après" say which way, a greyed arrow alone says nothing. */
@Composable
private fun MoveKey(icon: androidx.compose.ui.graphics.vector.ImageVector, word: String, spoken: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 64.dp).semantics { contentDescription = spoken }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(word, fontSize = 24.sp, maxLines = 1)
    }
}
