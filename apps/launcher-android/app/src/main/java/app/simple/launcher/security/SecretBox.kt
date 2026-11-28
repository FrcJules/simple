package app.simple.launcher.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts a small secret (the family code's hash) with AES-GCM. The key lives in the Android Keystore (hardware-backed where
 * available) and never leaves it: a copy of the app's files is not enough to read the secret.
 */
class SecretBox(private val key: () -> SecretKey) {
    /** `base64(iv + ciphertext)`, or null if encryption is not possible on this phone. */
    fun seal(plain: String): String? =
        try {
            val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: RuntimeException) {
            null // e.g. ProviderException from a faulty Keystore: "cannot seal", never a crash
        }

    /** The secret, or null if [sealed] was altered, was sealed with another key, or cannot be read. */
    fun open(sealed: String): String? =
        try {
            val data = Base64.decode(sealed, Base64.NO_WRAP)
            if (data.size <= IV_BYTES) null
            else {
                val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES)) }
                String(c.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
            }
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: RuntimeException) {
            null
        }

    companion object {
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        /** The app's key in the Android Keystore, created on first use. Throws [GeneralSecurityException] if there is no Keystore. */
        fun keystoreKey(alias: String): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return gen.generateKey()
        }

        /** Removes [alias] from the Android Keystore (nothing happens if it is not there). Throws [GeneralSecurityException] if there is no Keystore. */
        fun deleteKey(alias: String) {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }

        @Volatile private var cachedFamilyCode: SecretBox? = null

        /**
         * The family code's box, probed once it works. A probe that fails is not remembered: the Keystore can be briefly unavailable
         * (right after boot, low memory), and a lasting "no Keystore" must not be decided by one bad moment.
         */
        fun familyCode(): SecretBox? = cachedFamilyCode ?: forKeystore("simple-family-code")?.also { cachedFamilyCode = it }

        /** The Keystore-backed box, or null if this phone has no usable Keystore (the caller then keeps the old behaviour). */
        fun forKeystore(alias: String): SecretBox? =
            try {
                val key = keystoreKey(alias) // the key handle is kept: the Keystore is not queried again for every use
            SecretBox { key }.also { it.seal("probe") ?: return null }
            } catch (e: GeneralSecurityException) {
                null
            } catch (e: RuntimeException) {
                null
            }
    }
}
