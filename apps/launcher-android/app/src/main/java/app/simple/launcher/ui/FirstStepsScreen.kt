package app.simple.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simple.launcher.R

/**
 * Shown once at the first start, for whoever installs the app (the family): the three things Android asks for, each with its own
 * button and its state, and where the settings are afterwards. Nothing here is for Jean-Pierre.
 */
@Composable
fun FirstStepsScreen(
    homeDone: Boolean,
    contactsDone: Boolean,
    messagesDone: Boolean,
    onHome: () -> Unit,
    onContacts: () -> Unit,
    onMessages: () -> Unit,
    onAppSettings: () -> Unit,
    onFinish: () -> Unit,
) {
    SimpleTheme {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsTopBar(stringResource(R.string.steps_title), onBack = null)
            Text(stringResource(R.string.steps_intro), fontSize = 18.sp)
            SettingsCard { SetupChecklist(homeDone, contactsDone, messagesDone, onHome, onContacts, onMessages, onAppSettings) }
            Text(stringResource(R.string.steps_settings_hint), fontSize = 16.sp)
            if (homeDone && contactsDone && messagesDone) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.steps_finish), fontSize = 20.sp)
                }
            } else {
                // Something is still missing: leaving is possible, but it is not the main action.
                OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.steps_later), fontSize = 20.sp)
                }
            }
        }
    }
}
