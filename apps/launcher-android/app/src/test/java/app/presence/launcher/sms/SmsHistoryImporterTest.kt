package app.presence.launcher.sms

import android.provider.Telephony
import app.presence.launcher.messages.MessageStatus
import app.presence.launcher.messages.StoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsHistoryImporterTest {
    private fun row(id: Long, type: Int, body: String = "texte", address: String = "0611223344", read: Boolean = false) =
        ProviderSms(id, address, body, id * 1_000, type, read)

    private fun import(rows: List<ProviderSms>, names: Map<String, String> = emptyMap()): List<StoredMessage> {
        val out = mutableListOf<StoredMessage>()
        val n = SmsHistoryImporter(FakeGateway(rows), { out += it }, names::get).importAll()
        assertEquals(out.size, n)
        return out
    }

    @Test
    fun mapsDirectionStatusAndStableIds() {
        val m = import(
            listOf(
                row(1, Telephony.Sms.MESSAGE_TYPE_INBOX),
                row(2, Telephony.Sms.MESSAGE_TYPE_SENT),
                row(3, Telephony.Sms.MESSAGE_TYPE_FAILED),
                row(4, Telephony.Sms.MESSAGE_TYPE_OUTBOX),
            ),
        )
        assertEquals(listOf("sms-1", "sms-2", "sms-3", "sms-4"), m.map { it.id })
        assertEquals(listOf(false, true, true, true), m.map { it.outgoing })
        assertEquals(listOf(MessageStatus.NONE, MessageStatus.SENT, MessageStatus.FAILED, MessageStatus.SENDING), m.map { it.status })
    }

    @Test
    fun draftsAndEmptyRowsAreSkipped() {
        val m = import(
            listOf(
                row(1, Telephony.Sms.MESSAGE_TYPE_DRAFT),
                row(2, Telephony.Sms.MESSAGE_TYPE_INBOX, body = "  "),
                row(3, Telephony.Sms.MESSAGE_TYPE_INBOX, address = ""),
                row(4, Telephony.Sms.MESSAGE_TYPE_INBOX),
            ),
        )
        assertEquals(listOf("sms-4"), m.map { it.id })
    }

    @Test
    fun oldMessagesNeverRaiseTheNewMessagesBadge() {
        assertTrue(import(listOf(row(1, Telephony.Sms.MESSAGE_TYPE_INBOX, read = false))).single().read)
    }

    @Test
    fun usesContactNamesAndFallsBackToTheNumber() {
        val m = import(listOf(row(1, Telephony.Sms.MESSAGE_TYPE_INBOX)), mapOf("0611223344" to "Marie"))
        assertEquals("Marie", m.single().from)
        assertEquals("0611223344", import(listOf(row(1, Telephony.Sms.MESSAGE_TYPE_INBOX))).single().from)
    }

    @Test
    fun onlyReceivedSecurityCodesAreFlagged() {
        val code = "Code de confirmation 123456, ne le communiquez à personne"
        val m = import(listOf(row(1, Telephony.Sms.MESSAGE_TYPE_INBOX, code), row(2, Telephony.Sms.MESSAGE_TYPE_SENT, code)))
        assertTrue(m[0].sensitive)
        assertFalse(m[1].sensitive)
    }
}
