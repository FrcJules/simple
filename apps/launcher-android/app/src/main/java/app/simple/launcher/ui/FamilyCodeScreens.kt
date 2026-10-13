package app.simple.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simple.launcher.R
import app.simple.launcher.security.FamilyLock
import kotlinx.coroutines.delay

private fun digitsOnly(s: String) = s.filter { it in '0'..'9' }.take(FamilyLock.MAX_LENGTH)

/** One code field: numeric keypad, "Terminé" on the keyboard, and an eye to see what was typed (a wrong code nobody saw locks the family out). */
@Composable
private fun CodeField(value: String, label: String, onChange: (String) -> Unit, imeAction: ImeAction, onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, { onChange(digitsOnly(it)) },
        label = { Text(label) },
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = stringResource(if (shown) R.string.family_code_hide else R.string.family_code_show))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Asks for the family code before "Réglages famille". Drawn inside the activity's theme. While the screen is locked ([lockedSeconds] > 0)
 * the button is off and the remaining time counts down.
 */
@Composable
fun EnterFamilyCodeScreen(error: String?, lockedSeconds: Int, onEdit: () -> Unit, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var left by remember(lockedSeconds) { mutableStateOf(lockedSeconds) }
    LaunchedEffect(lockedSeconds) {
        left = lockedSeconds
        while (left > 0) { delay(1_000); left-- }
    }
    val submit = { if (left == 0 && code.length >= FamilyLock.MIN_LENGTH) { onSubmit(code); code = "" } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.family_code_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.family_code_for_family), fontSize = 24.sp, textAlign = TextAlign.Center)
        CodeField(code, stringResource(R.string.family_code_label), { code = it; onEdit() }, ImeAction.Done) { submit() }
        if (left > 0) Text(stringResource(R.string.family_code_locked, left), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        else if (error != null) Text(error, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Button(onClick = { submit() }, enabled = left == 0 && code.length >= FamilyLock.MIN_LENGTH, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.family_code_ok), fontSize = 24.sp)
        }
        BackButton(onBack)
    }
}

/** First use: the family chooses the code (twice). */
@Composable
fun SetFamilyCodeScreen(error: String?, onEdit: () -> Unit, onSubmit: (code: String, again: String) -> Unit, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val submit = { if (code.length >= FamilyLock.MIN_LENGTH && again.isNotEmpty()) { onSubmit(code, again); again = "" } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.family_code_new_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.family_code_for_family), fontSize = 24.sp, textAlign = TextAlign.Center)
        Text(stringResource(R.string.family_code_new_hint), fontSize = 24.sp, textAlign = TextAlign.Center)
        CodeField(code, stringResource(R.string.family_code_label), { code = it; onEdit() }, ImeAction.Next) {}
        CodeField(again, stringResource(R.string.family_code_again), { again = it; onEdit() }, ImeAction.Done) { submit() }
        if (error != null) Text(error, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Button(onClick = { submit() }, enabled = code.length >= FamilyLock.MIN_LENGTH && again.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.family_code_ok), fontSize = 24.sp)
        }
        BackButton(onBack)
    }
}

/**
 * The hidden gesture on the home screen was used before the family ever chose a code. Whoever holds the phone must not be able to
 * choose it then: the family chooses the code from the "Réglages" icon (or the QR code), never from Jean-Pierre's home screen.
 */
@Composable
fun FamilyCodeNotSetScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.family_code_not_set_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.family_code_not_set_hint), fontSize = 24.sp, textAlign = TextAlign.Center)
        BackButton(onBack)
    }
}
