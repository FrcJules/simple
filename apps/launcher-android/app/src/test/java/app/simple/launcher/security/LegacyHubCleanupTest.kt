package app.simple.launcher.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyHubCleanupTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val deleted = mutableListOf<String>()
    private fun cleanup(deleteKey: (String) -> Unit = { deleted += it }) = LegacyHubCleanup(context, deleteKey)
    private fun pairing() = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
    private fun profile() = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private fun pairingFile() = File(context.dataDir, "shared_prefs/pairing.xml")

    @Before fun fresh() {
        pairing().edit().clear().commit()
        profile().edit().clear().commit()
        context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** What an earlier version stored after pairing with the home display. */
    private fun oldPairing() {
        pairing().edit()
            .putString("url", "https://192.168.1.20:8788")
            .putString("pin", "A".repeat(64))
            .putString("token_sealed", "c2VhbGVk")
            .putString("token", "0123456789abcdef0123")
            .commit()
    }

    @Test
    fun theOldPairingAndItsKeyAreErased() {
        oldPairing()
        assertTrue(cleanup().runOnce())
        assertTrue(pairing().all.isEmpty())
        assertFalse(pairingFile().exists())
        assertEquals(listOf("simple-pairing"), deleted)
    }

    @Test
    fun ifThePairingCouldNotBeErasedItTriesAgainAtTheNextStart() {
        oldPairing()
        assertTrue(LegacyHubCleanup(context, { deleted += it }, erasePairing = { throw java.io.IOException("disk error") }).runOnce())
        assertTrue(pairingFile().exists()) // still there: not marked done
        assertTrue(cleanup().runOnce()) // next start: tried again, and this time it works
        assertFalse(pairingFile().exists())
        assertFalse(cleanup().runOnce())
    }

    @Test
    fun itRunsOnlyOnce() {
        oldPairing()
        assertTrue(cleanup().runOnce())
        oldPairing() // even if something wrote it again, the second start does not look
        assertFalse(cleanup().runOnce())
        assertEquals(1, deleted.size)
    }

    @Test
    fun theRestOfTheProfileAndTheFamilyCodeAreKept() {
        oldPairing()
        profile().edit().putString("name", "Jean-Pierre").putBoolean("first_steps_shown", true).commit()
        context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).edit().putString("sealed", "x").commit()
        cleanup().runOnce()
        assertEquals("Jean-Pierre", profile().getString("name", null))
        assertTrue(profile().getBoolean("first_steps_shown", false))
        assertEquals("x", context.getSharedPreferences("family_lock", Context.MODE_PRIVATE).getString("sealed", null))
    }

    @Test
    fun aKeystoreThatFailsDoesNotStopTheCleanupNorCrash() {
        oldPairing()
        assertTrue(cleanup { throw java.security.KeyStoreException("no keystore") }.runOnce())
        assertTrue(pairing().all.isEmpty())
        assertFalse(cleanup().runOnce()) // not tried again at every start
    }

    @Test
    fun aPhoneThatNeverPairedIsJustMarkedDone() {
        assertTrue(cleanup().runOnce())
        assertTrue(pairing().all.isEmpty())
        assertFalse(cleanup().runOnce())
    }
}
