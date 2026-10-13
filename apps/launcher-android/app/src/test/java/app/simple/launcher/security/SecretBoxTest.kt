package app.simple.launcher.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.simple.launcher.notifications.PairingConfig
import app.simple.launcher.notifications.PairingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecretBoxTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val key = newKey()
    private val box = SecretBox { key }

    @Before fun fresh() { context.getSharedPreferences("pairing", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test
    fun roundTrips() = assertEquals("0123456789abcdef0123", box.open(box.seal("0123456789abcdef0123")!!))

    @Test
    fun theSameSecretGivesADifferentCiphertextEachTime() = assertNotEquals(box.seal("secret"), box.seal("secret"))

    @Test
    fun theCiphertextDoesNotContainTheSecret() = assertFalse(String(android.util.Base64.decode(box.seal("0123456789abcdef0123"), android.util.Base64.NO_WRAP)).contains("0123456789"))

    @Test
    fun anAlteredCiphertextIsRefused() {
        val sealed = box.seal("secret")!!
        val raw = android.util.Base64.decode(sealed, android.util.Base64.NO_WRAP)
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 1).toByte()
        assertNull(box.open(android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)))
    }

    @Test
    fun anotherKeyCannotOpenIt() = assertNull(SecretBox { newKey() }.open(box.seal("secret")!!))

    @Test
    fun garbageIsRefusedNotACrash() {
        assertNull(box.open("not base64 !!"))
        assertNull(box.open(""))
        assertNull(box.open(android.util.Base64.encodeToString(ByteArray(5), android.util.Base64.NO_WRAP)))
    }

    private val config = PairingConfig.parse("https://192.168.1.20:8788", "0123456789abcdef0123", "A".repeat(64))!!

    @Test
    fun theTokenIsStoredEncryptedAndReadBack() {
        PairingStore(context, { box }).save(config)
        val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
        assertFalse(prefs.all.values.joinToString(" ").contains("0123456789abcdef"))
        assertTrue(prefs.contains("token_sealed") && !prefs.contains("token"))
        assertEquals(config, PairingStore(context, { box }).load())
    }

    @Test
    fun aTokenSavedInClearByAnEarlierVersionIsEncryptedOnFirstRead() {
        val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
        prefs.edit().putString("url", config.baseUrl).putString("pin", config.certSha256).putString("token", config.token).commit()
        assertEquals(config, PairingStore(context, { box }).load())
        assertFalse(prefs.contains("token"))
        assertTrue(prefs.contains("token_sealed"))
        assertEquals(config, PairingStore(context, { box }).load())
    }

    @Test
    fun withoutAKeystoreTheTokenIsKeptAsBeforeRatherThanLosingThePairing() {
        PairingStore(context, { null }).save(config)
        assertEquals(config, PairingStore(context, { null }).load())
    }

    @Test
    fun ifTheKeyIsLostThePairingMustBeDoneAgain() {
        PairingStore(context, { box }).save(config)
        assertNull(PairingStore(context, { SecretBox { newKey() } }).load())
    }

    @Test
    fun theProtectionStateTellsTheFamilyWhatIsHappening() {
        assertEquals(PairingStore.Protection.NONE, PairingStore(context, { box }).protectionState())
        PairingStore(context, { box }).save(config)
        assertEquals(PairingStore.Protection.ENCRYPTED, PairingStore(context, { box }).protectionState())
        assertEquals(PairingStore.Protection.UNREADABLE, PairingStore(context, { SecretBox { newKey() } }).protectionState())
        context.getSharedPreferences("pairing", Context.MODE_PRIVATE).edit().clear().commit()
        PairingStore(context, { null }).save(config)
        assertEquals(PairingStore.Protection.PLAIN, PairingStore(context, { null }).protectionState())
    }

    @Test
    fun aKeystoreThatThrowsIsAnUnreadableTokenNotACrashOfTheNotificationService() {
        PairingStore(context, { box }).save(config)
        val broken = SecretBox { throw java.security.ProviderException("keystore died") }
        assertNull(PairingStore(context, { broken }).load())
        assertNull(broken.seal("x"))
    }

    @Test
    fun aKeystoreThatIsBrieflyUnavailableIsNotRememberedAsNoKeystore() {
        var available = false
        val store = PairingStore(context, { if (available) box else null })
        store.save(config) // saved plain: no box at that moment
        assertEquals(PairingStore.Protection.PLAIN, store.protectionState())
        available = true
        assertEquals(config, store.load()) // the token is encrypted as soon as the Keystore answers
        assertEquals(PairingStore.Protection.ENCRYPTED, store.protectionState())
    }
}
