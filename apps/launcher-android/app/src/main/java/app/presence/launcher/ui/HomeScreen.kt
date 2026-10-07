package app.presence.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.R

@Composable
fun HomeScreen(seniorName: String, onTile: (HomeTile) -> Unit = {}) {
    PresenceTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val greeting = if (seniorName.isBlank()) stringResource(R.string.greeting_anonymous) else stringResource(R.string.greeting, seniorName)
            Text(greeting, fontSize = 32.sp, textAlign = TextAlign.Center)
            HomeTile.entries.chunked(2).forEach { pair ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { Tile(it, onTile) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Tile(tile: HomeTile, onTile: (HomeTile) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Card(
        Modifier.weight(1f).fillMaxSize()
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTile(tile)
            }
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(tile.icon, contentDescription = null, modifier = Modifier.size(72.dp))
                Text(stringResource(tile.label), fontSize = 24.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
