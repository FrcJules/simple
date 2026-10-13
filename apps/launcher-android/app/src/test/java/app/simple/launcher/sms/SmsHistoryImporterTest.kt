package app.simple.launcher.sms

import android.provider.Telephony
import app.simple.launcher.messages.MessageStatus
import app.simple.launcher.messages.StoredMessage
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

    private fun sync(rows: List<ProviderSms>, afterId: Long, keepRead: Boolean, out: MutableList<StoredMessage>) =
        SmsHistoryImporter(FakeGateway(rows), { out += it }, { null }).syncSince(afterId, keepRead)

    @Test
    fun aSyncOnlyCopiesWhatCameAfterTheLastCopiedRowAndReportsTheNewLastId() {
        val rows = listOf(row(1, Telephony.Sms.MESSAGE_TYPE_INBOX), row(2, Telephony.Sms.MESSAGE_TYPE_INBOX), row(5, Telephony.Sms.MESSAGE_TYPE_INBOX))
        val out = mutableListOf<StoredMessage>()
        val first = sync(rows, 0, keepRead = false, out)
        assertEquals(3, first.count)
        assertEquals(5L, first.lastId)
        out.clear()
        val again = sync(rows, first.lastId, keepRead = true, out)
        assertEquals(0, again.count)
        assertEquals(5L, again.lastId) // nothing new: the marker does not move back
        val more = sync(rows + row(9, Telephony.Sms.MESSAGE_TYPE_INBOX), again.lastId, keepRead = true, out)
        assertEquals(listOf("sms-9"), out.map { it.id })
        assertEquals(9L, more.lastId)
    }

    @Test
    fun theFirstCopyMarksEverythingReadButLaterOnesKeepTheSystemFlag() {
        val rows = listOf(row(1, Telephony.Sms.MESSAGE_TYPE_INBOX, read = false), row(2, Telephony.Sms.MESSAGE_TYPE_INBOX, read = true))
        val first = mutableListOf<StoredMessage>()
        sync(rows, 0, keepRead = false, first)
        assertEquals(listOf(true, true), first.map { it.read }) // an old inbox must not raise a huge badge
        val later = mutableListOf<StoredMessage>()
        sync(rows, 0, keepRead = true, later)
        assertEquals(listOf(false, true), later.map { it.read }) // a new unread SMS counts as new
    }

    @Test
    fun sentMessagesAreAlwaysReadWhateverTheFlag() {
        val out = mutableListOf<StoredMessage>()
        sync(listOf(row(1, Telephony.Sms.MESSAGE_TYPE_SENT, read = false)), 0, keepRead = true, out)
        assertTrue(out.single().read)
    }

    @Test
    fun theSystemRecordOfAMessageWeSentOurselvesIsNotAddedAgain() {
        val mine = StoredMessage("out-1", "Jean-Baptiste", app.simple.launcher.notifications.Channel.SMS, "Salut", 5_000L, read = true, outgoing = true, address = "0611223344")
        val rows = listOf(row(1, Telephony.Sms.MESSAGE_TYPE_SENT, body = "Salut").copy(dateMs = 5_100L), row(2, Telephony.Sms.MESSAGE_TYPE_INBOX, body = "Reçu").copy(dateMs = 6_000L))
        val out = mutableListOf<StoredMessage>()
        val result = SmsHistoryImporter(FakeGateway(rows), { out += it }, { null }, sentSince = { listOf(mine) }).syncSince(0, keepReadFlag = true)
        assertEquals(listOf("sms-2"), out.map { it.id }) // the sent row is our own message; the received one is new
        assertEquals(1, result.count)
        assertEquals(2L, result.lastId) // but both rows are behind us now
    }

    @Test
    fun twoIdenticalSendsStayTwoOneRecordHidesOnlyOne() {
        fun mine(id: String, at: Long) = StoredMessage(id, "X", app.simple.launcher.notifications.Channel.SMS, "Ok", at, read = true, outgoing = true, address = "0611223344")
        val rows = listOf(
            row(1, Telephony.Sms.MESSAGE_TYPE_SENT, body = "Ok").copy(dateMs = 5_000L),
            row(2, Telephony.Sms.MESSAGE_TYPE_SENT, body = "Ok").copy(dateMs = 6_000L),
            row(3, Telephony.Sms.MESSAGE_TYPE_SENT, body = "Ok").copy(dateMs = 7_000L),
        )
        val out = mutableListOf<StoredMessage>()
        SmsHistoryImporter(FakeGateway(rows), { out += it }, { null }, sentSince = { listOf(mine("out-a", 5_000L), mine("out-b", 6_000L)) }).syncSince(0, keepReadFlag = true)
        assertEquals(listOf("sms-3"), out.map { it.id }) // two of the three rows are our own two sends
    }
}

