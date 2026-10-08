package app.presence.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.R
import app.presence.launcher.security.FamilyLock

private fun digitsOnly(s: String) = s.filter { it.isDigit() }.take(FamilyLock.MAX_LENGTH)

/** Asks for the family code before "Réglages famille". Drawn inside the activity's theme. */
@Composable
fun EnterFamilyCodeScreen(error: String?, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.family_code_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        OutlinedTextField(
            code, { code = digitsOnly(it) }, label = { Text(stringResource(R.string.family_code_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Button(onClick = { onSubmit(code); code = "" }, enabled = code.length >= FamilyLock.MIN_LENGTH, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.family_code_ok), fontSize = 24.sp)
        }
    }
}

/** First use: the family chooses the code (twice). */
@Composable
fun SetFamilyCodeScreen(error: String?, onSubmit: (code: String, again: String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.family_code_new_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.family_code_new_hint), fontSize = 24.sp, textAlign = TextAlign.Center)
        val numeric = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        OutlinedTextField(code, { code = digitsOnly(it) }, label = { Text(stringResource(R.string.family_code_label)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(again, { again = digitsOnly(it) }, label = { Text(stringResource(R.string.family_code_again)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error != null) Text(error, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Button(onClick = { onSubmit(code, again) }, enabled = code.length >= FamilyLock.MIN_LENGTH, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.family_code_ok), fontSize = 24.sp)
        }
    }
}
