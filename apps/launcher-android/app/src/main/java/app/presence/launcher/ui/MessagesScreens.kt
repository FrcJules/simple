package app.presence.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
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
    val text = when {
        m.mms && !m.outgoing && m.status == MessageStatus.SENDING -> return stringResource(R.string.mms_receiving)
        m.mms && !m.outgoing && m.status == MessageStatus.FAILED -> return stringResource(R.string.mms_failed)
        m.text.isBlank() && m.attachments.isNotEmpty() -> stringResource(R.string.message_photo)
        else -> m.text
    }
    return when {
        m.sensitive -> stringResource(R.string.message_sensitive)
        m.outgoing -> stringResource(R.string.message_you, text)
        else -> text
    }
}

@Composable
private fun MessageRow(item: MessageItem, nowMs: Long, onOpen: (MessageItem) -> Unit) {
    val unread = item.unread > 0
    // A conversation with something new does not look like the others: tinted card, thick frame, a count on the picture and the words
    // (never colour alone). Read conversations stay plain.
    Card(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item) }.semantics(mergeDescendants = true) {},
        colors = if (unread) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
        border = if (unread) BorderStroke(4.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BadgedBox(badge = {
                if (unread) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("${item.unread}", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 6.dp)) }
            }) {
                Avatar(item.displayContact, Modifier.size(80.dp), initialSize = 40.sp)
            }
            Column(Modifier.weight(1f)) {
                if (unread) {
                    Text(
                        pluralStringResource(R.plurals.message_new_count, item.unread, item.unread),
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    )
                }
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
    onRetry: (StoredMessage) -> Unit = {},
    onImage: (String) -> Unit = {},
    onSendPhoto: (() -> Unit)? = null,
    onOpenWhatsApp: (() -> Unit)? = null,
) {
    PresenceTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(item.displayContact, Modifier.size(72.dp), initialSize = 36.sp)
                Text(item.message.from, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Newest at the bottom, anchored there: a new message never makes the list jump while someone reads further up.
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages.asReversed(), key = { it.id }) { Bubble(it, item.message.from, nowMs, onRetry, onImage) }
            }
            if (notice != null) Text(notice, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (onOpenWhatsApp != null) {
                // The WhatsApp notification (our only way in) is gone: say so, and offer the one thing that works.
                Text(stringResource(R.string.whatsapp_open_hint), fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                // The only action that works here: a filled button, with an "opens another app" icon (not an SMS icon).
                Button(onClick = onOpenWhatsApp, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.whatsapp_open), fontSize = 24.sp)
                }
            }
            val canListen = messages.any { !it.outgoing && !it.sensitive && it.text.isNotBlank() } // a picture alone has nothing to read
            val canWrite = item.canReply && canDictate
            var heard by remember { mutableStateOf(false) }
            // Fixed places (Écouter left, Répondre right); only the emphasis moves: listen first while something new waits.
            val listenIsMain = canListen && !heard && (item.unread > 0 || !canWrite)
            if (item.canReply && !canDictate) {
                Text(stringResource(R.string.dictate_unavailable), fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (canListen || canWrite) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (canListen) ListenButton({ heard = true; onListen() }, main = listenIsMain, modifier = Modifier.weight(1f))
                    if (canWrite) ReplyButton(onReply, main = !listenIsMain, isNew = messages.isEmpty(), modifier = Modifier.weight(1f))
                }
            }
            if (onSendPhoto != null) {
                OutlinedButton(onClick = onSendPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.photo_send), fontSize = 24.sp)
                }
            }
            BackButton(onBack)
        }
    }
}

/** One message. Direction is not carried by colour alone: ours sit on the right and are labelled "Vous". */
@Composable
private fun Bubble(m: StoredMessage, otherName: String, nowMs: Long, onRetry: (StoredMessage) -> Unit, onImage: (String) -> Unit) {
    var revealed by remember(m.id) { mutableStateOf(false) }
    val masked = m.sensitive && !revealed
    val receiving = m.mms && !m.outgoing && m.status == MessageStatus.SENDING // still downloading
    val notArrived = m.mms && !m.outgoing && m.status == MessageStatus.FAILED // download failed: can be retried
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.outgoing) Arrangement.End else Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(0.82f).then(if (masked) Modifier.clickable(role = Role.Button) { revealed = true } else Modifier),
            colors = CardDefaults.cardColors(containerColor = if (m.outgoing) colors.primaryContainer else colors.surfaceContainerHighest),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(if (m.outgoing) stringResource(R.string.message_you_label) else otherName, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val body = when {
                    receiving -> stringResource(R.string.mms_receiving)
                    notArrived -> stringResource(R.string.mms_failed)
                    masked -> stringResource(R.string.message_sensitive) + "\n" + stringResource(R.string.message_sensitive_tap)
                    else -> m.text
                }
                if (receiving || notArrived) {
                    // A picture is understood faster than a sentence: a spinner while it comes, a broken picture when it did not.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (receiving) CircularProgressIndicator(Modifier.size(40.dp)) else Icon(Icons.Filled.BrokenImage, contentDescription = null, modifier = Modifier.size(40.dp))
                        Text(body, fontSize = 28.sp)
                    }
                } else if (body.isNotBlank()) {
                    Text(body, fontSize = 28.sp)
                }
                if (!receiving && !notArrived && !masked) {
                    val total = m.attachments.size
                    m.attachments.forEachIndexed { i, a ->
                        AsyncImage(
                            model = File(a.path),
                            contentDescription = if (total > 1) stringResource(R.string.message_photo_n, i + 1, total) else stringResource(R.string.message_photo),
                            contentScale = ContentScale.Fit,
                            error = rememberVectorPainter(Icons.Filled.BrokenImage), // missing or unreadable file: never a blank hole
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp, max = 260.dp).padding(vertical = 4.dp)
                                .clickable(role = Role.Button) { onImage(a.path) },
                        )
                    }
                    if (total > 0) Text(stringResource(R.string.photo_tap_to_enlarge), fontSize = 24.sp)
                }
                Text(
                    listOfNotNull(agoText(m.receivedAtMs, nowMs), if (m.outgoing) statusText(m.status) else null).joinToString(" · "),
                    fontSize = 24.sp,
                    fontWeight = if (m.outgoing && m.status == MessageStatus.FAILED) FontWeight.Bold else FontWeight.Normal,
                )
                if ((m.outgoing && m.status == MessageStatus.FAILED) || notArrived) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onRetry(m) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (notArrived) R.string.mms_retry else R.string.message_retry), fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun statusText(status: MessageStatus): String? = when (status) {
    MessageStatus.SENDING -> stringResource(R.string.status_sending)
    MessageStatus.SENT -> stringResource(R.string.status_sent)
    MessageStatus.DELIVERED -> stringResource(R.string.status_delivered)
    MessageStatus.HANDED_OFF -> stringResource(R.string.status_handed_off)
    MessageStatus.FAILED -> stringResource(R.string.status_failed)
    MessageStatus.NONE -> null
}

/** Icon above the label: two of these share a row, and a label such as "Répondre" must never be split across lines. */
@Composable
private fun StackedLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null)
        Text(label, fontSize = 24.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ListenButton(onClick: () -> Unit, main: Boolean, modifier: Modifier = Modifier) {
    val content: @Composable () -> Unit = { StackedLabel(Icons.Filled.VolumeUp, stringResource(R.string.message_listen)) }
    if (main) Button(onClick = onClick, modifier = modifier.heightIn(min = 88.dp)) { content() }
    else OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 88.dp)) { content() }
}

@Composable
private fun ReplyButton(onClick: () -> Unit, main: Boolean, isNew: Boolean, modifier: Modifier = Modifier) {
    val content: @Composable () -> Unit = { StackedLabel(Icons.Filled.Mic, stringResource(if (isNew) R.string.message_dictate else R.string.message_reply)) }
    if (main) Button(onClick = onClick, modifier = modifier.heightIn(min = 88.dp)) { content() }
    else OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 88.dp)) { content() }
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
    isNew: Boolean = false,
    notice: String? = null,
    whatsapp: Boolean = false,
    onOpenWhatsApp: (() -> Unit)? = null,
) {
    PresenceTheme {
        // The whole screen scrolls: on a small screen or with a large font the text to check must never be squeezed out.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(item.displayContact, Modifier.fillMaxWidth(0.28f).aspectRatio(1f))
            Text(stringResource(if (whatsapp) R.string.reply_to_whatsapp else if (isNew) R.string.write_to else R.string.reply_to, item.message.from), fontSize = 28.sp, textAlign = TextAlign.Center)
            Text(text, fontSize = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
            if (notice != null) Text(notice, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(stringResource(if (whatsapp) R.string.reply_hint_whatsapp else if (directSend) R.string.reply_hint_direct else R.string.reply_hint), fontSize = 24.sp, textAlign = TextAlign.Center)
            if (onOpenWhatsApp != null) {
                Button(onClick = onOpenWhatsApp, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.whatsapp_open), fontSize = 28.sp)
                }
            }
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(if (whatsapp) R.string.reply_send_whatsapp else if (directSend) R.string.reply_send_direct else R.string.reply_send), fontSize = 28.sp)
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
