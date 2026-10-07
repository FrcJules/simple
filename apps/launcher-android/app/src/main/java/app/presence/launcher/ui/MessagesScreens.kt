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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.StoredMessage
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
private fun preview(item: MessageItem): String {
    val m = item.message
    return when {
        m.sensitive -> stringResource(R.string.message_sensitive)
        m.outgoing -> stringResource(R.string.message_you, m.text)
        else -> m.text
    }
}

@Composable
private fun MessageRow(item: MessageItem, nowMs: Long, onOpen: (MessageItem) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item) }.semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(item.displayContact, Modifier.size(80.dp), initialSize = 40.sp)
            Column(Modifier.weight(1f)) {
                // Not colour alone: unread messages also carry a word, above the name so the text keeps the full width.
                if (item.unread > 0) Text(stringResource(R.string.message_new), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(item.message.from, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(preview(item), fontSize = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(agoText(item.message.receivedAtMs, nowMs), fontSize = 24.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun ConversationScreen(
    item: MessageItem,
    messages: List<StoredMessage>,
    nowMs: Long,
    canDictate: Boolean,
    notice: String?,
    onListen: () -> Unit,
    onReply: () -> Unit,
    onBack: () -> Unit,
) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(item.displayContact, Modifier.size(72.dp), initialSize = 36.sp)
                Text(item.message.from, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages, key = { it.id }) { Bubble(it, nowMs) }
            }
            if (notice != null) Text(notice, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val canListen = messages.any { !it.outgoing && !it.sensitive }
            // One main action: listen while something new is waiting, reply once it has been heard.
            val listenIsMain = canListen && (item.unread > 0 || !item.canReply || !canDictate)
            if (listenIsMain) ListenButton(onListen, main = true)
            if (item.canReply && canDictate) {
                ReplyButton(onReply, main = !listenIsMain)
            } else if (item.canReply) {
                Text(stringResource(R.string.dictate_unavailable), fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (canListen && !listenIsMain) ListenButton(onListen, main = false)
            BackButton(onBack)
        }
    }
}

/** One message. Direction is not carried by colour alone: ours sit on the right and say "Vous". */
@Composable
private fun Bubble(m: StoredMessage, nowMs: Long) {
    var revealed by remember(m.id) { mutableStateOf(false) }
    val masked = m.sensitive && !revealed
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.outgoing) Arrangement.End else Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f).then(if (masked) Modifier.clickable(role = Role.Button) { revealed = true } else Modifier),
            colors = CardDefaults.cardColors(containerColor = if (m.outgoing) colors.primaryContainer else colors.surfaceContainerHighest),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (masked) stringResource(R.string.message_sensitive) + "\n" + stringResource(R.string.message_sensitive_tap) else m.text,
                    fontSize = 28.sp,
                )
                val status = if (m.outgoing) statusText(m.status) else null
                Text(
                    listOfNotNull(if (m.outgoing) stringResource(R.string.message_you_label) else null, agoText(m.receivedAtMs, nowMs), status).joinToString(" · "),
                    fontSize = 24.sp,
                    fontWeight = if (m.outgoing && m.status == MessageStatus.FAILED) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun statusText(status: MessageStatus): String? = when (status) {
    MessageStatus.SENDING -> stringResource(R.string.status_sending)
    MessageStatus.SENT -> stringResource(R.string.status_sent)
    MessageStatus.DELIVERED -> stringResource(R.string.status_delivered)
    MessageStatus.FAILED -> stringResource(R.string.status_failed)
    MessageStatus.NONE -> null
}

@Composable
private fun ListenButton(onClick: () -> Unit, main: Boolean) {
    val content: @Composable () -> Unit = {
        Icon(Icons.Filled.VolumeUp, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.message_listen), fontSize = 28.sp)
    }
    if (main) Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { content() }
    else OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { content() }
}

@Composable
private fun ReplyButton(onClick: () -> Unit, main: Boolean) {
    val content: @Composable () -> Unit = {
        Icon(Icons.Filled.Mic, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.message_reply), fontSize = 28.sp)
    }
    if (main) Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { content() }
    else OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { content() }
}

/**
 * Shows what was dictated before anything happens. As the default SMS app ([directSend]) we send it ourselves; otherwise
 * the messaging app opens on a draft and the person taps its send arrow, so the screen says so instead of promising "Envoyer".
 */
@Composable
fun ReplyConfirmScreen(
    item: MessageItem,
    text: String,
    onListen: () -> Unit,
    onSend: () -> Unit,
    onRedo: () -> Unit,
    onCancel: () -> Unit,
    directSend: Boolean = false,
) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(item.displayContact, Modifier.fillMaxWidth(0.4f).aspectRatio(1f))
            Text(stringResource(R.string.reply_to, item.message.from), fontSize = 28.sp, textAlign = TextAlign.Center)
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Text(stringResource(if (directSend) R.string.reply_hint_direct else R.string.reply_hint), fontSize = 24.sp, textAlign = TextAlign.Center)
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(if (directSend) R.string.reply_send_direct else R.string.reply_send), fontSize = 28.sp)
            }
            OutlinedButton(onClick = onListen, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.reply_listen), fontSize = 28.sp)
            }
            OutlinedButton(onClick = onRedo, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.reply_redo), fontSize = 28.sp)
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.reply_cancel), fontSize = 28.sp)
            }
        }
    }
}
