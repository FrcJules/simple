package app.presence.launcher.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The family code that protects "Réglages famille" (pairing, erasing, appearance) from whoever holds the phone. Only a salted
 * hash (PBKDF2) is kept, never the code, and where the phone has a Keystore that hash is itself sealed with a key that never leaves
 * it: a copy of the app's files (root, a forensic image) cannot be tried offline against all the 4-to-8-digit codes. After repeated mistakes the screen locks for longer and longer, so the code cannot
 * be tried exhaustively on the phone. There is no recovery: forgetting it means clearing the app's data in Android settings.
 *
 * The lock is measured with the phone's monotonic clock only, never with the date (which anyone can change). A restart cannot be
 * timed by that clock, so it starts the lock again for its whole length: restarting the phone never shortens a wait, and the wait
 * never exceeds [MAX_LOCK_SECONDS].
 */
class FamilyLock(
    context: Context,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val bootCount: () -> Int = { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) },
    private val box: () -> SecretBox? = { SecretBox.familyCode() },
) {
    private val prefs = context.applicationContext.getSharedPreferences("family_lock", Context.MODE_PRIVATE)

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val triesLeft: Int) : Result
        data class Locked(val seconds: Int) : Result
    }

    enum class Problem { LENGTH, OBVIOUS }

    fun isSet(): Boolean = (prefs.contains("hash") || prefs.contains("sealed")) && prefs.contains("salt")

    /** Seconds still to wait, 0 if the code can be tried now. */
    fun lockedSeconds(): Int = synchronized(LOCK) { lockedSecondsLocked() }

    private fun lockedSecondsLocked(): Int {
        val lockMs = prefs.getLong("lock_ms", 0)
        if (lockMs <= 0) return 0
        val now = elapsed()
        val sameBoot = prefs.getInt("lock_boot", -1) == bootCount() && now >= prefs.getLong("lock_elapsed", 0)
        if (!sameBoot) {
            // The phone restarted since the mistake (a clock that went backwards says so even where the boot counter is unavailable):
            // the time already waited is unknown, so the whole wait starts again from now.
            prefs.edit().putInt("lock_boot", bootCount()).putLong("lock_elapsed", now).commit()
        }
        val remaining = lockMs - (now - prefs.getLong("lock_elapsed", 0))
        if (remaining <= 0) {
            prefs.edit().putLong("lock_ms", 0).commit() // served: a later restart must not start it again
            return 0
        }
        return ((minOf(remaining, MAX_LOCK_SECONDS * 1000L) + 999) / 1000).toInt()
    }

    /** Why [code] is not acceptable (null if it is): 4 to 8 digits, and not an obvious one. */
    fun problemWith(code: String): Problem? = when {
        code.length !in MIN_LENGTH..MAX_LENGTH || !code.all { it in '0'..'9' } -> Problem.LENGTH
        isObvious(code) -> Problem.OBVIOUS
        else -> null
    }

    fun set(code: String): Boolean = synchronized(LOCK) { setLocked(code) }

    private fun setLocked(code: String): Boolean {
        if (problemWith(code) != null) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val edit = prefs.edit().putString("salt", hex(salt)).putInt("failures", 0).putLong("lock_ms", 0)
        storeHash(edit, hex(hash(code, salt)))
        edit.commit()
        return true
    }

    fun verify(code: String): Result = synchronized(LOCK) { verifyLocked(code) }

    private fun verifyLocked(code: String): Result {
        val locked = lockedSecondsLocked()
        if (locked > 0) return Result.Locked(locked)
        val salt = prefs.getString("salt", null)?.let(::unhex)
        val sealed = prefs.getString("sealed", null)
        // Once the hash has been sealed, a plain "hash" is never accepted again (it could only have been put there from outside).
        val wasSealed = sealed != null || prefs.getBoolean("sealed_once", false)
        val stored = when {
            sealed != null -> box()?.open(sealed)
            wasSealed -> null
            else -> prefs.getString("hash", null)
        }
        val expected = stored?.let(::unhex)
        if (!wasSealed && (salt == null || expected == null)) {
            // Unreadable or missing record of a code that was never sealed (not a state we write): start again rather than lock out for good.
            prefs.edit().remove("hash").remove("sealed").remove("salt").commit()
            return Result.Wrong(0)
        }
        // A sealed hash that cannot be opened (Keystore briefly unavailable, or its key was lost) is never wiped here: it counts as a
        // wrong code, so nobody can reset the code by making the Keystore fail. A lost key means clearing the app's data, as documented.
        if (salt != null && expected != null && MessageDigest.isEqual(hash(code, salt), expected)) {
            val reset = prefs.edit().putInt("failures", 0).putLong("lock_ms", 0)
            if (sealed == null) storeHash(reset, hex(expected)) // a code chosen before the Keystore was used is sealed now
            reset.commit()
            return Result.Ok
        }
        // Nothing could be checked (the Keystore is briefly unavailable or its key is gone): that is not a mistake of the family's, so it
        // neither counts nor locks. It cannot be used to guess either, since no code is compared.
        if (wasSealed && expected == null) return Result.Wrong(FREE_TRIES - prefs.getInt("failures", 0) % FREE_TRIES)
        val failures = prefs.getInt("failures", 0) + 1
        val edit = prefs.edit().putInt("failures", failures)
        if (failures % FREE_TRIES == 0) {
            // 30 s, 60 s, 2 min, ... up to 15 minutes
            val seconds = minOf(30L shl (failures / FREE_TRIES - 1).coerceAtMost(5), MAX_LOCK_SECONDS.toLong())
            edit.putLong("lock_ms", seconds * 1000).putLong("lock_elapsed", elapsed()).putInt("lock_boot", bootCount())
            edit.commit() // written now: a crash right after a mistake must not forget it
            return Result.Locked(seconds.toInt())
        }
        edit.commit()
        return Result.Wrong(FREE_TRIES - failures % FREE_TRIES)
    }

    /** Seals the hash when the phone has a Keystore; otherwise (or if sealing fails) it is kept as before, protected by PBKDF2 alone. */
    private fun storeHash(edit: android.content.SharedPreferences.Editor, hashHex: String) {
        val sealed = box()?.seal(hashHex)
        if (sealed != null) edit.putString("sealed", sealed).putBoolean("sealed_once", true).remove("hash") else edit.putString("hash", hashHex).remove("sealed")
    }

    private fun hash(code: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun isObvious(code: String): Boolean {
        if (code.all { it == code[0] }) return true // 1111
        val steps = code.zipWithNext { a, b -> b - a }
        return steps.all { it == 1 } || steps.all { it == -1 } // 1234, 4321
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String): ByteArray? =
        if (s.length % 2 != 0 || !s.all { it in '0'..'9' || it in 'a'..'f' }) null else ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    companion object {
        /** One lock for every instance: the preferences are shared by the whole process. */
        private val LOCK = Any()
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 8
        const val FREE_TRIES = 5
        const val MAX_LOCK_SECONDS = 900
        private const val ITERATIONS = 120_000
    }
}
