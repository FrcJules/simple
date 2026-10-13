package app.simple.launcher.notifications

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Certificate pinning: the hub's self-signed certificate is trusted only if its SHA-256 matches the pairing fingerprint. */
object CertificatePin {
    /** Uppercase hex without separators, or null if it is not a SHA-256 (64 hex chars). Accepts `AA:BB:…` and spaces. */
    fun normalize(fingerprint: String): String? {
        val hex = fingerprint.filterNot { it == ':' || it.isWhitespace() }.uppercase()
        return if (hex.length == 64 && hex.all { it in '0'..'9' || it in 'A'..'F' }) hex else null
    }

    fun sha256Hex(der: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02X".format(it) }

    fun matches(der: ByteArray, pinnedHex: String): Boolean =
        MessageDigest.isEqual(sha256Hex(der).toByteArray(), pinnedHex.toByteArray())

    /** TLS context that trusts exactly one certificate. Hostname checks are replaced by the pin. */
    fun sslContext(pinnedHex: String): SSLContext {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                throw CertificateException("client certificates are not used")

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("empty chain")
                if (!matches(leaf.encoded, pinnedHex)) throw CertificateException("certificate does not match the pairing fingerprint")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    }
}
