package app.presence.launcher.mms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.MessageStore
import app.presence.launcher.messages.StoredMessage
import app.presence.launcher.messages.Attachment
import app.presence.launcher.notifications.Channel
import app.presence.launcher.notifications.Incoming
import app.presence.launcher.notifications.SensitiveText
import app.presence.launcher.sms.MmsDownloadedReceiver
import app.presence.launcher.sms.smsManagerFor

/** The platform side of MMS: download a message announced by the operator, and send a PDU to the operator. */
interface MmsTransport {
    /** Downloads [location] into [target]; [done] is broadcast with the result. Throws SecurityException / IllegalArgumentException if refused. */
    fun download(location: String, target: Uri, subId: Int, done: PendingIntent)

    /** Posts the PDU stored at [pdu] to the operator's MMSC (acknowledgements, outgoing pictures); [sent] reports the outcome. */
    fun post(pdu: Uri, subId: Int, sent: PendingIntent? = null)
}

class SmsManagerMmsTransport(private val context: Context) : MmsTransport {
    override fun download(location: String, target: Uri, subId: Int, done: PendingIntent) {
        smsManagerFor(context, subId).downloadMultimediaMessage(context, location, target, null, done)
    }

    override fun post(pdu: Uri, subId: Int, sent: PendingIntent?) {
        smsManagerFor(context, subId).sendMultimediaMessage(context, pdu, null, null, sent)
    }
}

/**
 * Receiving an MMS: the operator announces it, we show a "being received" bubble at once, the platform downloads it, we keep
 * the text and pictures (private files), acknowledge to the operator and announce it. Nothing is ever silently lost: a
 * download that fails leaves a visible "not received" message with a retry.
 */
class MmsInbox(
    private val context: Context,
    private val store: MessageStore,
    private val files: MmsFiles,
    private val transport: MmsTransport,
    private val nameFor: (String) -> String?,
    private val forward: (Incoming) -> Unit,
    private val notify: (StoredMessage) -> Unit,
    private val unknownSender: String,
    private val photoLabel: String,
    /** Last check before a picture is kept: it must decode and not be absurdly large (the system decoders are the attack surface). */
    private val imageOk: (ReceivedImage) -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun onNotification(pdu: ByteArray, subId: Int) {
        val n = MmsPdu.parseNotification(pdu) ?: return
        val id = "mms-" + MmsFiles.hash("${n.transactionId}|${n.location}")
        if (store.exists(id)) return // the operator repeats its notification until it is acknowledged
        val now = clock()
        files.cleanOldTransfers(now - OLD_TRANSFER_MS)
        // Anyone able to send a WAP push could flood us with announcements, each one a download on the mobile plan:
        // beyond these limits new ones are ignored (the operator will announce them again later).
        if (store.pendingCount() >= MAX_PENDING || store.mmsReceivedSince(now - HOUR_MS) >= MAX_PER_HOUR) return
        val name = n.from?.let { nameFor(it) ?: it } ?: unknownSender
        store.add(StoredMessage(id, name, Channel.SMS, "", now, address = n.from, status = MessageStatus.SENDING, mms = true))
        val pending = MessageStore.PendingMms(id, n.transactionId, n.location, subId, tries = 0, sinceMs = now)
        store.putPending(pending)
        if (n.sizeBytes > MmsPdu.MAX_PDU_BYTES) {
            fail(id) // too big to handle safely: shown as not received instead of being downloaded
            return
        }
        startDownload(pending)
    }

    /** The person asked to try again (or the process was restarted). */
    fun retry(messageId: String) {
        val p = store.pending(messageId) ?: return
        store.setStatus(messageId, MessageStatus.SENDING)
        startDownload(p)
    }

    fun onDownloaded(messageId: String, transferName: String, ok: Boolean) {
        val file = files.transferFile(transferName)
        try {
            val p = store.pending(messageId) ?: return // already handled (or the history was erased meanwhile): nothing is kept
            if (!ok || file == null) {
                fail(messageId)
                return
            }
            val bytes = files.readTransfer(file)
            val mms = bytes?.let { MmsPdu.parseRetrieveConf(it) }
            if (mms == null) {
                fail(messageId)
                return
            }
            complete(messageId, p, mms)
        } finally {
            files.deleteQuietly(file)
            files.cleanOldTransfers(clock() - OLD_TRANSFER_MS)
        }
    }

    private fun complete(id: String, p: MessageStore.PendingMms, mms: ReceivedMms) {
        val now = clock()
        val pictures = mms.images.filter(imageOk).mapIndexedNotNull { i, img -> files.savePicture(id, i, img)?.let { Attachment(img.mime, it.path) } }
        val sensitive = SensitiveText.looksSensitive(mms.text)
        val at = minOf(mms.dateMs ?: now, now) // never in the future
        // The announcement can be forged by anyone; the sender written in the downloaded message is the better evidence.
        val sender = mms.from
        try {
            store.completeMms(id, mms.text, at, pictures, sensitive, sender = sender?.let { nameFor(it) }, senderAddress = sender)
        } catch (e: RuntimeException) {
            files.deletePictures(id) // do not leave pictures that no message points to
            fail(id)
            return
        }
        acknowledge(p)
        val message = store.messages.value.firstOrNull { it.id == id } ?: return
        // The home display is visible from the whole room: known people only, never a picture, never a security code.
        if (!sensitive && message.address != null && nameFor(message.address) != null) {
            runCatching { forward(Incoming.Message(message.from.take(60), Channel.SMS, mms.text.ifBlank { photoLabel }.take(500))) }
        }
        runCatching { notify(message) }
    }

    /** Without it some operators keep re-sending the same message. Best effort: the message is already ours. */
    private fun acknowledge(p: MessageStore.PendingMms) {
        val bytes = MmsPdu.notifyResponse(context, p.transactionId) ?: return
        val file = files.writeTransfer(bytes) ?: return
        try {
            transport.post(files.uriFor(file), p.subscription)
        } catch (e: RuntimeException) {
            // refused: the transfer file is removed with the old ones
        }
    }

    private fun startDownload(p: MessageStore.PendingMms) {
        val file = files.newTransferFile()
        try {
            store.putPending(p.copy(tries = p.tries + 1))
            val done = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, MmsDownloadedReceiver::class.java)
                    .setAction(ACTION_DOWNLOADED)
                    .setData(Uri.parse("presence-mms://${p.messageId}/${file.name}")) // distinct per attempt
                    .putExtra(EXTRA_ID, p.messageId)
                    .putExtra(EXTRA_FILE, file.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            transport.download(p.location, files.uriFor(file), p.subscription, done)
        } catch (e: SecurityException) {
            files.deleteQuietly(file)
            fail(p.messageId)
        } catch (e: IllegalArgumentException) {
            files.deleteQuietly(file)
            fail(p.messageId)
        }
    }

    private fun fail(id: String) = store.setStatus(id, MessageStatus.FAILED)

    companion object {
        const val ACTION_DOWNLOADED = "app.presence.launcher.MMS_DOWNLOADED"
        const val EXTRA_ID = "message_id"
        const val EXTRA_FILE = "transfer_file"
        private const val OLD_TRANSFER_MS = 60 * 60 * 1000L
        private const val HOUR_MS = 60 * 60 * 1000L
        const val MAX_PENDING = 20
        const val MAX_PER_HOUR = 60
    }
}
