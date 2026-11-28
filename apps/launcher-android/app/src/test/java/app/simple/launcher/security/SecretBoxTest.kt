package app.simple.launcher.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecretBoxTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val key = newKey()
    private val box = SecretBox { key }

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

    @Test
    fun aKeystoreThatThrowsCannotSealOrOpenButNeverCrashes() {
        val sealed = box.seal("secret")!!
        val broken = SecretBox { throw java.security.ProviderException("keystore died") }
        assertNull(broken.seal("x"))
        assertNull(broken.open(sealed))
    }
}
