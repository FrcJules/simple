package app.presence.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.R

/** Plain-language explanation shown before the system permission dialog, which the user would otherwise refuse out of fear. */
@Composable
fun PermissionRationaleScreen(onContinue: () -> Unit, onLater: () -> Unit) {
    PresenceTheme {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(96.dp))
            Text(stringResource(R.string.rationale_text), fontSize = 28.sp, textAlign = TextAlign.Center)
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.rationale_continue), fontSize = 28.sp)
            }
            OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.rationale_later), fontSize = 28.sp)
            }
        }
    }
}
