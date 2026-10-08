package app.presence.launcher.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.R
import coil.compose.AsyncImage

/** Where does the picture to send come from: the camera, or a picture already on the phone (chosen by the person, one at a time). */
@Composable
fun PhotoChoiceScreen(name: String, canTakePhoto: Boolean, onTake: () -> Unit, onPick: () -> Unit, onBack: () -> Unit) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.photo_send_to, name), fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
            }
            BackButton(onBack)
        }
    }
}

/** Shows exactly what will be sent, and to whom, before anything leaves the phone. */
@Composable
fun PhotoConfirmScreen(
    name: String,
    photo: Uri,
    notice: String?,
    onSend: () -> Unit,
    onChange: () -> Unit,
    onCancel: () -> Unit,
) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.photo_send_question, name), fontSize = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            AsyncImage(
                model = photo,
                contentDescription = stringResource(R.string.message_photo),
                contentScale = ContentScale.Fit,
                error = rememberVectorPainter(Icons.Filled.BrokenImage),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            if (notice != null) Text(notice, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.reply_send_direct), fontSize = 28.sp)
            }
            OutlinedButton(onClick = onChange, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.photo_other), fontSize = 24.sp)
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.reply_cancel), fontSize = 24.sp)
            }
        }
    }
}
