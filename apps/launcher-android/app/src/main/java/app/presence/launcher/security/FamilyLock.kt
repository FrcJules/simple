package app.presence.launcher.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The family code that protects "Réglages famille" (pairing, erasing, appearance) from whoever holds the phone. Only a salted
 * hash (PBKDF2) is kept, never the code. After repeated mistakes the screen locks for longer and longer, so the code cannot
 * be tried exhaustively on the phone. There is no recovery: forgetting it means clearing the app's data in Android settings.
 */
class FamilyLock(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.applicationContext.getSharedPreferences("family_lock", Context.MODE_PRIVATE)

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val triesLeft: Int) : Result
        data class Locked(val seconds: Int) : Result
    }

    fun isSet(): Boolean = prefs.contains("hash")

    /** Why [code] is not acceptable (null if it is): 4 to 8 digits, and not an obvious one. */
    fun problemWith(code: String): Problem? = when {
        code.length !in MIN_LENGTH..MAX_LENGTH || !code.all { it.isDigit() } -> Problem.LENGTH
        isObvious(code) -> Problem.OBVIOUS
        else -> null
    }

    enum class Problem { LENGTH, OBVIOUS }

    fun set(code: String): Boolean {
        if (problemWith(code) != null) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("salt", hex(salt)).putString("hash", hex(hash(code, salt)))
            .putInt("failures", 0).putLong("locked_until", 0)
            .apply()
        return true
    }

    fun verify(code: String): Result {
        val now = clock()
        val lockedUntil = prefs.getLong("locked_until", 0)
        if (now < lockedUntil) return Result.Locked(((lockedUntil - now + 999) / 1000).toInt())
        val salt = prefs.getString("salt", null)?.let(::unhex)
        val expected = prefs.getString("hash", null)?.let(::unhex)
        if (salt == null || expected == null) return Result.Wrong(0)
        if (MessageDigest.isEqual(hash(code, salt), expected)) {
            prefs.edit().putInt("failures", 0).putLong("locked_until", 0).apply()
            return Result.Ok
        }
        val failures = prefs.getInt("failures", 0) + 1
        val edit = prefs.edit().putInt("failures", failures)
        if (failures % FREE_TRIES == 0) {
            // 30 s, 60 s, 2 min, ... up to 15 minutes
            val round = failures / FREE_TRIES
            val seconds = minOf(30L shl (round - 1).coerceAtMost(5), 900L)
            edit.putLong("locked_until", now + seconds * 1000)
            edit.apply()
            return Result.Locked(seconds.toInt())
        }
        edit.apply()
        return Result.Wrong(FREE_TRIES - failures % FREE_TRIES)
    }

    private fun hash(code: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256)).encoded

    private fun isObvious(code: String): Boolean {
        if (code.all { it == code[0] }) return true // 1111
        val steps = code.zipWithNext { a, b -> b - a }
        return steps.all { it == 1 } || steps.all { it == -1 } // 1234, 4321
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 8
        const val FREE_TRIES = 5
        private const val ITERATIONS = 120_000
    }
}
