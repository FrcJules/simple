package app.simple.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
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
import app.simple.launcher.R

/** Plain-language explanation shown before Android asks "Use Simple as your SMS app?", which would otherwise be refused out of fear. */
@Composable
fun SmsRoleScreen(onContinue: () -> Unit, onLater: () -> Unit) {
    SimpleTheme {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(96.dp))
            Text(stringResource(R.string.sms_role_text), fontSize = 28.sp, textAlign = TextAlign.Center)
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.sms_role_continue), fontSize = 28.sp)
            }
            OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Text(stringResource(R.string.sms_role_later), fontSize = 28.sp)
            }
        }
    }
}
