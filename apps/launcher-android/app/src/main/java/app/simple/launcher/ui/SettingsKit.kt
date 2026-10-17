package app.simple.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simple.launcher.R

// The family settings are read by the family, not by Jean-Pierre: ordinary sizes (still >= 48 dp targets), grouped in cards.
// Colors only come from the theme.

/** Title line of a settings screen, with a back arrow when it is not the first one. */
@Composable
fun SettingsTopBar(title: String, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.setup_back))
            }
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** One entry of the settings menu: icon, title, a short line saying what is inside or what is missing. */
@Composable
fun SettingsMenuItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 15.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

/** A step that is done or not: the state is written ("Fait" / "À faire") besides the icon, never carried by the icon alone. */
@Composable
fun StepRow(label: String, done: Boolean, actionLabel: String, help: String? = null, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (done) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(stringResource(if (done) R.string.step_done else R.string.step_todo), fontSize = 16.sp)
        }
        if (!done) {
            if (help != null) Text(help, fontSize = 16.sp)
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(actionLabel, fontSize = 18.sp) }
        }
    }
}

/** The three things to do once on a new phone. Shared by the first-start guide and the top of the settings. */
@Composable
fun SetupChecklist(
    homeDone: Boolean,
    contactsDone: Boolean,
    messagesDone: Boolean,
    onHome: () -> Unit,
    onContacts: () -> Unit,
    onMessages: () -> Unit,
    onAppSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepRow(stringResource(R.string.step_home), homeDone, stringResource(R.string.step_home_action), null, onHome)
        StepRow(stringResource(R.string.step_contacts), contactsDone, stringResource(R.string.step_contacts_action), null, onContacts)
        StepRow(stringResource(R.string.step_messages), messagesDone, stringResource(R.string.step_messages_action), stringResource(R.string.step_messages_help), onMessages)
        if (!(homeDone && contactsDone && messagesDone)) {
            Text(stringResource(R.string.step_refused_help), fontSize = 15.sp)
            OutlinedButton(onClick = onAppSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.step_open_app_settings), fontSize = 16.sp)
            }
        }
    }
}
