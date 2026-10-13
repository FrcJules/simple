package app.simple.launcher.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilyLockTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var now = 1_000_000L // the date (can be changed by the user)
    private var mono = 5_000L // the monotonic clock (cannot)
    private var boot = 1

    @Before fun fresh() { context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().clear().commit() }

    private var sealing: SecretBox? = null
    private fun lock() = FamilyLock(context, elapsed = { mono }, bootCount = { boot }, box = { sealing })
    private fun prefs() = context.getSharedPreferences("family_lock", Context.MODE_PRIVATE)
    private fun pass(ms: Long) { now += ms; mono += ms }

    @Test
    fun nothingIsSetAtFirst() {
        assertFalse(lock().isSet())
        assertEquals(FamilyLock.Result.Wrong(0), lock().verify("2580"))
    }

    @Test
    fun theRightCodeOpensAndAWrongOneDoesNot() {
        val l = lock()
        assertTrue(l.set("2580"))
        assertTrue(l.isSet())
        assertEquals(FamilyLock.Result.Ok, l.verify("2580"))
        assertEquals(FamilyLock.Result.Wrong(4), l.verify("2581"))
        assertEquals(FamilyLock.Result.Wrong(3), l.verify("0000"))
    }

    @Test
    fun theCodeItselfIsNeverStored() {
        lock().set("2580")
        val all = context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).all.values.joinToString(" ")
        assertFalse(all.contains("2580"))
        assertTrue(context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).getString("hash", "")!!.length == 64) // 256-bit hash
    }

    @Test
    fun theSameCodeGivesADifferentHashEachTime() {
        lock().set("2580")
        val first = context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).getString("hash", "")
        lock().set("2580")
        assertFalse(first == context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).getString("hash", ""))
    }

    @Test
    fun staysSetAfterARestart() {
        lock().set("2580")
        assertEquals(FamilyLock.Result.Ok, lock().verify("2580"))
    }

    @Test
    fun lockedAfterFiveMistakesForLongerAndLonger() {
        val l = lock()
        l.set("2580")
        repeat(4) { l.verify("9999") }
        assertEquals(FamilyLock.Result.Locked(30), l.verify("9999"))
        // even the right code is refused while locked
        assertTrue(l.verify("2580") is FamilyLock.Result.Locked)
        assertEquals(30, l.lockedSeconds())
        pass(31_000)
        assertEquals(0, l.lockedSeconds())
        repeat(4) { assertTrue(l.verify("9999") is FamilyLock.Result.Wrong) }
        assertEquals(FamilyLock.Result.Locked(60), l.verify("9999")) // doubled
    }

    @Test
    fun theLockLastsAtMostFifteenMinutes() {
        val l = lock()
        l.set("2580")
        var last: FamilyLock.Result = FamilyLock.Result.Ok
        repeat(60) { last = l.verify("9999"); if (last is FamilyLock.Result.Locked) pass(3_600_000) }
        assertEquals(FamilyLock.Result.Locked(900), last)
    }

    @Test
    fun aSuccessClearsTheMistakes() {
        val l = lock()
        l.set("2580")
        repeat(3) { l.verify("9999") }
        assertEquals(FamilyLock.Result.Ok, l.verify("2580"))
        assertEquals(FamilyLock.Result.Wrong(4), l.verify("9999"))
    }

    @Test
    fun theLockSurvivesARestartSoItCannotBeResetByClosingTheScreen() {
        val l = lock()
        l.set("2580")
        repeat(5) { l.verify("9999") }
        assertTrue(lock().verify("2580") is FamilyLock.Result.Locked)
    }

    @Test
    fun obviousOrMalformedCodesAreRefused() {
        val l = lock()
        listOf("1111", "0000", "1234", "4321", "123456", "98765").forEach { assertEquals(FamilyLock.Problem.OBVIOUS, l.problemWith(it)) }
        listOf("123", "123456789", "12a4", "", "12 34").forEach { assertEquals(FamilyLock.Problem.LENGTH, l.problemWith(it)) }
        assertNull(l.problemWith("2580"))
        assertNull(l.problemWith("13579"))
        assertFalse(l.set("1234"))
        assertFalse(l.isSet())
    }

    @Test
    fun movingTheDateForwardDoesNotLiftALockOnTheSameBoot() {
        val l = lock()
        l.set("2580")
        repeat(5) { l.verify("9999") }
        now += 10L * 365 * 24 * 3600 * 1000 // the date is set 10 years ahead; the monotonic clock did not move
        assertTrue(l.verify("2580") is FamilyLock.Result.Locked)
    }

    @Test
    fun restartingThePhoneAndMovingTheDateNeverShortensTheWait() {
        val l = lock()
        l.set("2580")
        repeat(5) { l.verify("9999") }
        boot += 1
        mono = 10L // the monotonic clock restarted
        now += 365L * 24 * 3600 * 1000 // and the date was moved a year ahead
        assertEquals(30, l.lockedSeconds()) // the whole wait starts again
        assertTrue(l.verify("2580") is FamilyLock.Result.Locked)
        now -= 2L * 365 * 24 * 3600 * 1000 // or two years back: no effect either
        mono += 10_000
        assertEquals(20, l.lockedSeconds())
        mono += 21_000
        assertEquals(0, l.lockedSeconds())
    }

    @Test
    fun aClockThatWentBackwardsCountsAsARestartEvenIfTheBootCounterIsUnavailable() {
        val l = lock()
        l.set("2580")
        repeat(5) { l.verify("9999") }
        mono = 1L // restarted, but bootCount() still says the same value
        assertEquals(30, l.lockedSeconds())
    }

    @Test
    fun aWaitThatWasServedIsNotStartedAgainByALaterRestart() {
        val l = lock()
        l.set("2580")
        repeat(5) { l.verify("9999") }
        pass(31_000)
        assertEquals(0, l.lockedSeconds())
        boot += 1
        mono = 10L
        assertEquals(0, l.lockedSeconds())
    }

    @Test
    fun theWaitNeverExceedsFifteenMinutes() {
        val l = lock()
        l.set("2580")
        repeat(40) { if (l.lockedSeconds() > 0) pass(l.lockedSeconds() * 1000L + 1); l.verify("9999") }
        assertTrue(l.lockedSeconds() <= FamilyLock.MAX_LOCK_SECONDS)
    }

    private fun softBox() = SecretBox { javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }

    @Test
    fun withAKeystoreTheHashIsSealedAndNeverStoredAsIs() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        sealing = SecretBox { key }
        val l = lock()
        l.set("2580")
        assertFalse(prefs().contains("hash"))
        assertTrue(prefs().contains("sealed"))
        assertTrue(l.isSet())
        assertEquals(FamilyLock.Result.Ok, l.verify("2580"))
        assertEquals(FamilyLock.Result.Wrong(4), l.verify("9999"))
    }

    @Test
    fun aCopyOfTheFilesOnAnotherKeyCannotBeOpenedNorReset() {
        sealing = softBox()
        lock().set("2580")
        sealing = softBox() // the Keystore key is not the one that sealed it (copied files, lost key)
        val l = lock()
        assertTrue(l.verify("2580") is FamilyLock.Result.Wrong)
        assertTrue(l.isSet()) // not wiped: nobody can reset the code by making the Keystore fail
    }

    @Test
    fun aKeystoreThatIsBrieflyUnavailableDoesNotWipeTheCode() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        sealing = SecretBox { key }
        lock().set("2580")
        sealing = null
        assertTrue(lock().verify("2580") is FamilyLock.Result.Wrong)
        assertTrue(lock().isSet())
        sealing = SecretBox { key }
        assertEquals(FamilyLock.Result.Ok, lock().verify("2580"))
    }

    @Test
    fun onceSealedAPlainHashPutBackFromOutsideIsNeverAccepted() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        sealing = SecretBox { key }
        lock().set("2580")
        val salt = prefs().getString("salt", null)!!
        // someone writes a plain record for a code they know and removes the sealed one
        prefs().edit().remove("sealed").putString("hash", "00".repeat(32)).commit()
        assertTrue(lock().verify("1357") is FamilyLock.Result.Wrong)
        assertEquals(salt, prefs().getString("salt", null)) // and nothing was wiped
        assertTrue(lock().verify("2580") is FamilyLock.Result.Wrong)
    }

    @Test
    fun aKeystoreThatCannotBeReadIsNotCountedAsMistakes() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        sealing = SecretBox { key }
        lock().set("2580")
        sealing = null
        repeat(12) { assertTrue(lock().verify("2580") is FamilyLock.Result.Wrong) }
        assertEquals(0, lock().lockedSeconds()) // no lock was imposed on the family
        sealing = SecretBox { key }
        assertEquals(FamilyLock.Result.Ok, lock().verify("2580"))
    }

    @Test
    fun aDamagedSaltOfASealedCodeCountsAsAMistakeAndWipesNothing() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        sealing = SecretBox { key }
        lock().set("2580")
        prefs().edit().putString("salt", "zz").commit()
        assertTrue(lock().verify("2580") is FamilyLock.Result.Wrong)
        assertTrue(lock().isSet())
    }

    @Test
    fun aCodeChosenBeforeTheKeystoreWasUsedIsSealedAtTheNextSuccess() {
        sealing = null
        lock().set("2580")
        assertTrue(prefs().contains("hash"))
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        sealing = SecretBox { key }
        assertEquals(FamilyLock.Result.Ok, lock().verify("2580"))
        assertFalse(prefs().contains("hash"))
        assertTrue(prefs().contains("sealed"))
        assertEquals(FamilyLock.Result.Ok, lock().verify("2580"))
    }

    @Test
    fun aCorruptedRecordResetsInsteadOfLockingTheFamilyOutForGood() {
        val l = lock()
        l.set("2580")
        context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().putString("hash", "zz-not-hex").commit()
        assertEquals(FamilyLock.Result.Wrong(0), l.verify("2580"))
        assertFalse(l.isSet()) // starts again: a new code can be chosen
    }

    @Test
    fun onlyAsciiDigitsAreAccepted() {
        assertEquals(FamilyLock.Problem.LENGTH, lock().problemWith("٢٥٨٠")) // Arabic-Indic digits
    }
}
