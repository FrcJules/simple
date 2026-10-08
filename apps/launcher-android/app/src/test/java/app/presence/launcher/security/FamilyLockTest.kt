package app.presence.launcher.security

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

    private fun lock() = FamilyLock(context, wallClock = { now }, elapsed = { mono }, bootCount = { boot })
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
    fun aDateSetFarAheadAndBackNeverLocksForMoreThanFifteenMinutes() {
        val l = lock()
        l.set("2580")
        now += 50L * 365 * 24 * 3600 * 1000
        repeat(5) { l.verify("9999") } // locked "from the year 2076"
        now -= 50L * 365 * 24 * 3600 * 1000 // the date goes back to normal
        boot += 1 // after a restart only the date can be used
        assertTrue(l.lockedSeconds() <= FamilyLock.MAX_LOCK_SECONDS)
    }

    @Test
    fun afterARestartTheLockStillHoldsUsingTheDate() {
        val l = lock()
        l.set("2580")
        repeat(5) { l.verify("9999") }
        boot += 1
        mono = 10L // the monotonic clock restarted
        now += 5_000
        assertEquals(25, l.lockedSeconds())
        now += 26_000
        assertEquals(0, l.lockedSeconds())
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
