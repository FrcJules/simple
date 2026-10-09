package app.presence.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.runtime.remember
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.R
import app.presence.launcher.config.TileAction
import app.presence.launcher.config.TileConfig
import app.presence.launcher.config.UiConfig

@Composable
fun HomeScreen(
    seniorName: String,
    unreadMessages: Int = 0,
    config: UiConfig = UiConfig.Default,
    status: HomeStatus? = null,
    onSecretTap: () -> Unit = {},
    onTile: (TileConfig) -> Unit = {},
    showSetupHint: Boolean = false,
    onSetup: () -> Unit = {},
) {
    PresenceTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (status != null) StatusBar(status, onSecretTap)
            val greeting = if (seniorName.isBlank()) stringResource(R.string.greeting_anonymous) else stringResource(R.string.greeting, seniorName)
            Text(greeting, fontSize = 32.sp, textAlign = TextAlign.Center)
            config.home.tiles.chunked(config.home.columns).forEach { rowTiles ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowTiles.forEach { Tile(it, if (it.action == TileAction.MESSAGES) unreadMessages else 0, onTile) }
                    // Keep tile widths equal when the last row is not full.
                    repeat(config.home.columns - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // Until the family has chosen a code: the one visible way in to the settings (afterwards only the hidden gesture and the "Réglages" icon).
            if (showSetupHint) {
                OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    Text(stringResource(R.string.home_setup_hint), fontSize = 24.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Tile(tile: TileConfig, badge: Int, onTile: (TileConfig) -> Unit) {
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
                BadgedBox(badge = {
                    if (badge > 0) Badge { Text("$badge", fontSize = 22.sp, modifier = Modifier.padding(horizontal = 6.dp)) }
                }) {
                    Icon(tile.icon.vector(), contentDescription = null, modifier = Modifier.size(72.dp))
                }
                Text(tile.label ?: stringResource(tile.action.defaultLabel), fontSize = 24.sp, textAlign = TextAlign.Center)
                if (badge > 0) {
                    Text(
                        when {
                            badge == 1 -> stringResource(R.string.tile_new_one)
                            badge > 9 -> stringResource(R.string.tile_new_many)
                            else -> stringResource(R.string.tile_new_count, badge)
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Time, date and battery. The clock is also the family's hidden way into the settings (no visible hint). */
@Composable
private fun StatusBar(status: HomeStatus, onSecretTap: () -> Unit) {
    val counter = remember { SecretTapCounter() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.heightIn(min = 64.dp).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                if (counter.tap(System.currentTimeMillis())) onSecretTap()
            },
        ) {
            Text(status.time, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(status.date, fontSize = 24.sp)
        }
        val pct = status.batteryPercent
        if (pct != null) {
            // Icon + words carry the state; colour is never the only signal.
            val label = when {
                status.charging -> stringResource(R.string.battery_charging, pct)
                status.batteryLow -> stringResource(R.string.battery_low, pct)
                else -> stringResource(R.string.battery_level, pct)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    when {
                        status.charging -> Icons.Filled.BatteryChargingFull
                        status.batteryLow -> Icons.Filled.BatteryAlert
                        else -> Icons.Filled.BatteryFull
                    },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Text(label, fontSize = 24.sp, fontWeight = if (status.batteryLow && !status.charging) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
