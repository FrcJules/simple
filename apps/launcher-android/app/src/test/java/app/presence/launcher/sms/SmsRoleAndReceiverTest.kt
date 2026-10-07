package app.presence.launcher.sms

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import app.presence.launcher.messages.MessageStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsRoleAndReceiverTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun fresh() = MessageStore.replaceInstanceForTests(null)
    @After fun cleanUp() = MessageStore.replaceInstanceForTests(null)

    @Test
    fun weAreNotTheDefaultSmsAppByDefault() = assertFalse(SmsRole.isHeld(context))

    @Test
    fun aRealGsmPduIsDecodedAndStored() {
        val intent = Intent(Telephony.Sms.Intents.SMS_DELIVER_ACTION)
            .putExtra("pdus", arrayOf(SmsPdu.deliver("+33611223344", "Bonjour Jean-Pierre, a demain")))
            .putExtra("format", "3gpp")
        SmsDeliverReceiver().onReceive(context, intent)

        val store = MessageStore.get(context)
        val deadline = System.currentTimeMillis() + 5_000
        while (store.count() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)

        val m = store.messages.value.single()
        assertEquals("+33611223344", m.address)
        assertEquals("Bonjour Jean-Pierre, a demain", m.text)
        assertFalse(m.outgoing)
        assertFalse(m.read)
    }

    @Test
    fun theHistoryDatabaseOfTheFirstVersionIsUpgradedWithoutLosingMessages() {
        val name = "upgrade-test.db"
        context.deleteDatabase(name)
        val old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        old.execSQL(
            """CREATE TABLE messages (id TEXT PRIMARY KEY, who TEXT NOT NULL, address TEXT, thread TEXT NOT NULL,
               channel TEXT NOT NULL, outgoing INTEGER NOT NULL DEFAULT 0, body TEXT NOT NULL, at INTEGER NOT NULL,
               read INTEGER NOT NULL DEFAULT 0, status INTEGER NOT NULL DEFAULT 0)""",
        )
        old.execSQL("INSERT INTO messages VALUES ('a','Marie','+1','sms:short:1','sms',0,'Bonjour',1000,0,0)")
        old.version = 1
        old.close()

        val store = MessageStore(context, name, legacyFile = null)
        val m = store.messages.value.single()
        assertEquals("Bonjour", m.text)
        assertFalse(m.sensitive)
        context.deleteDatabase(name)
    }
}
