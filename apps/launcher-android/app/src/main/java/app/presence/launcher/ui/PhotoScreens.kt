package app.presence.launcher.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.Contact
import app.presence.launcher.R
import coil.compose.AsyncImage

/** Who will receive the picture, as a face and a name (and the number when we do not know the person). */
@Composable
private fun Recipient(contact: Contact, known: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(contact, Modifier.size(72.dp), initialSize = 36.sp)
            Text(contact.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!known) Text(stringResource(R.string.reply_unknown_number), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Where does the picture to send come from: the camera, or a picture on the phone (chosen by the person, one at a time). */
@Composable
fun PhotoChoiceScreen(
    contact: Contact,
    known: Boolean,
    canTakePhoto: Boolean,
    notice: String?,
    onTake: () -> Unit,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.photo_send_to), fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Recipient(contact, known)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                if (canTakePhoto) {
                    Button(onClick = onTake, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.photo_take), fontSize = 28.sp)
                    }
                }
                OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.photo_choose), fontSize = 28.sp)
                }
                if (notice != null) Text(notice, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            BackButton(onBack)
        }
    }
}

/** Shows exactly what will be sent, and to whom, before anything leaves the phone. */
@Composable
fun PhotoConfirmScreen(
    contact: Contact,
    known: Boolean,
    photo: Uri,
    notice: String?,
    sending: Boolean,
    onSend: () -> Unit,
    onNotThisOne: () -> Unit,
) {
    PresenceTheme {
        var unreadable by remember(photo) { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.photo_send_question), fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Recipient(contact, known)
            AsyncImage(
                model = photo,
                contentDescription = stringResource(R.string.photo_to_send_for, contact.name),
                contentScale = ContentScale.Fit,
                error = rememberVectorPainter(Icons.Filled.BrokenImage),
                onError = { unreadable = true },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            if (notice != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(36.dp))
                    Text(notice, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            // A picture we cannot even see must not be sent; and a second touch while it goes must not send it twice.
            Button(onClick = onSend, enabled = !sending && !unreadable, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(if (sending) R.string.photo_sending else R.string.reply_send_direct), fontSize = 28.sp)
            }
            OutlinedButton(onClick = onNotThisOne, enabled = !sending, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.photo_other), fontSize = 24.sp)
            }
        }
    }
}
