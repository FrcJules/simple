package app.presence.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presence.launcher.R
import app.presence.launcher.messages.AgoUnit
import app.presence.launcher.messages.MessageItem
import app.presence.launcher.messages.ago

@Composable
private fun agoText(thenMs: Long, nowMs: Long): String {
    val a = ago(thenMs, nowMs)
    val res = LocalContext.current.resources
    return when {
        a.unit == AgoUnit.NOW -> stringResource(R.string.ago_now)
        a.unit == AgoUnit.MINUTES -> res.getString(if (a.count == 1) R.string.ago_minute else R.string.ago_minutes, a.count)
        a.unit == AgoUnit.HOURS -> res.getString(if (a.count == 1) R.string.ago_hour else R.string.ago_hours, a.count)
        else -> res.getString(if (a.count == 1) R.string.ago_day else R.string.ago_days, a.count)
    }
}

@Composable
fun MessagesScreen(
    items: List<MessageItem>,
    nowMs: Long,
    onOpen: (MessageItem) -> Unit,
    onWrite: () -> Unit,
    onBack: () -> Unit,
) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.messages_title), fontSize = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (items.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.messages_empty), fontSize = 28.sp, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items, key = { it.message.id }) { MessageRow(it, nowMs, onOpen) }
                }
            }
            OutlinedButton(onClick = onWrite, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.Sms, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.messages_write), fontSize = 24.sp)
            }
            BackButton(onBack)
        }
    }
}

@Composable
private fun MessageRow(item: MessageItem, nowMs: Long, onOpen: (MessageItem) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item) }.semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(item.displayContact, Modifier.size(80.dp), initialSize = 40.sp)
            Column(Modifier.weight(1f)) {
                Text(item.message.from, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.message.text, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(agoText(item.message.receivedAtMs, nowMs), fontSize = 24.sp)
            }
            // Not colour alone: unread messages also carry a word.
            if (!item.message.read) Text(stringResource(R.string.message_new), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MessageDetailScreen(
    item: MessageItem,
    nowMs: Long,
    onListen: () -> Unit,
    onReply: () -> Unit,
    onBack: () -> Unit,
) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(item.displayContact, Modifier.fillMaxWidth(0.45f).aspectRatio(1f))
            Text(item.message.from, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(agoText(item.message.receivedAtMs, nowMs), fontSize = 24.sp)
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(item.message.text, fontSize = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = onListen, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.message_listen), fontSize = 28.sp)
            }
            if (item.canReply) {
                Button(onClick = onReply, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.message_reply), fontSize = 28.sp)
                }
            }
            BackButton(onBack)
        }
    }
}

/** Shows what was dictated before anything is sent, with a big way out. */
@Composable
fun ReplyConfirmScreen(item: MessageItem, text: String, onSend: () -> Unit, onRedo: () -> Unit, onCancel: () -> Unit) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(item.displayContact, Modifier.fillMaxWidth(0.4f).aspectRatio(1f))
            Text(stringResource(R.string.reply_to, item.message.from), fontSize = 28.sp, textAlign = TextAlign.Center)
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.reply_send), fontSize = 28.sp)
            }
            OutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.reply_redo), fontSize = 28.sp)
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.call_no), fontSize = 28.sp)
            }
        }
    }
}
