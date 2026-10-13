package app.simple.launcher.mms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.simple.launcher.isDialable
import app.simple.launcher.messages.Attachment
import app.simple.launcher.messages.MessageStatus
import app.simple.launcher.messages.MessageStore
import app.simple.launcher.messages.StoredMessage
import app.simple.launcher.notifications.Channel
import app.simple.launcher.sms.MmsSentReceiver
import java.util.UUID

sealed interface MmsSendResult {
    data class Started(val messageId: String) : MmsSendResult
    data object NotDefaultApp : MmsSendResult
    data object InvalidNumber : MmsSendResult

    /** The picture could not be read or reduced to a size an operator accepts. */
    data object Unreadable : MmsSendResult
}

/**
 * Sends one picture by MMS to one person (only when we are the default SMS app). The picture is reduced to a small JPEG, kept
 * as a private file next to the message (so a failure can be retried without asking for the photo again), and the outcome is
 * shown in the conversation: sending / sent / not sent.
 */
class MmsSender(
    private val context: Context,
    private val store: MessageStore,
    private val files: MmsFiles,
    private val transport: MmsTransport,
    private val compress: (Uri) -> ByteArray?,
    private val isDefaultApp: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun send(address: String, name: String, photo: Uri, subId: Int = -1): MmsSendResult {
        if (!isDefaultApp()) return MmsSendResult.NotDefaultApp
        if (!isDialable(address)) return MmsSendResult.InvalidNumber
        val jpeg = compress(photo) ?: return MmsSendResult.Unreadable
        val id = "mms-out-${UUID.randomUUID()}"
        val saved = files.savePicture(id, 0, ReceivedImage("image/jpeg", jpeg)) ?: return MmsSendResult.Unreadable
        store.addMms(
            StoredMessage(id, name, Channel.SMS, "", clock(), read = true, outgoing = true, address = address, status = MessageStatus.SENDING, mms = true),
            listOf(Attachment("image/jpeg", saved.path)),
        )
        transmit(id, address, jpeg, subId)
        return MmsSendResult.Started(id)
    }

    /** Tries again with the picture already kept with the message. */
    fun retry(messageId: String, subId: Int = -1) {
        val m = store.get(messageId) ?: return
        if (!m.outgoing || !m.mms || m.status != MessageStatus.FAILED || !isDefaultApp()) return // only a failed picture is sent again
        val address = m.address ?: return
        val jpeg = m.attachments.firstOrNull()?.let { files.readPicture(it.path) }
        if (jpeg == null) {
            store.setStatus(messageId, MessageStatus.FAILED)
            return
        }
        store.setStatus(messageId, MessageStatus.SENDING)
        transmit(messageId, address, jpeg, subId)
    }

    private fun transmit(id: String, address: String, jpeg: ByteArray, subId: Int) {
        val pdu = MmsPdu.sendRequest(context, address, jpeg)
        val file = pdu?.let { files.writeTransfer(it) }
        if (file == null) {
            store.setStatus(id, MessageStatus.FAILED)
            return
        }
        try {
            val sent = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, MmsSentReceiver::class.java)
                    .setAction(ACTION_SENT)
                    .setData(Uri.parse("simple-mms-out://$id/${file.name}")) // distinct per attempt
                    .putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_FILE, file.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            transport.post(files.uriFor(file), subId, sent)
        } catch (e: RuntimeException) {
            // Whatever the platform refuses with, the launcher (the home screen) must not crash and the PDU must not stay on disk.
            files.deleteQuietly(file)
            store.setStatus(id, MessageStatus.FAILED)
        }
    }

    /** The platform answered: [ok] means the operator accepted the message. */
    fun onSent(id: String, transferName: String, ok: Boolean) {
        files.deleteQuietly(files.transferFile(transferName))
        if (store.status(id) == MessageStatus.SENDING) store.setStatus(id, if (ok) MessageStatus.SENT else MessageStatus.FAILED)
    }

    companion object {
        const val ACTION_SENT = "app.simple.launcher.MMS_SENT"
        const val EXTRA_ID = "message_id"
        const val EXTRA_FILE = "transfer_file"
    }
}
