package app.presence.launcher.notifications

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** The network part of pairing, behind an interface so it can be tested. */
interface PairingTransport {
    /** POSTs `{"claim": …}` to `<baseUrl>/pair` over HTTPS pinned to [certSha256]. Null if the hub could not be reached. */
    fun redeem(baseUrl: String, certSha256: String, claim: String): TransportResponse?
}

class TransportResponse(val status: Int, val body: String)

class HttpsPairingTransport : PairingTransport {
    override fun redeem(baseUrl: String, certSha256: String, claim: String): TransportResponse? =
        try {
            val url = URL("$baseUrl/pair")
            // The same rules as for events: only a home-network address, whatever the name resolves to, no redirect.
            if (!InetAddress.getAllByName(url.host).all { it.isSiteLocalAddress }) {
                null
            } else {
                val conn = url.openConnection() as HttpsURLConnection
                try {
                    conn.sslSocketFactory = CertificatePin.sslContext(certSha256).socketFactory
                    conn.setHostnameVerifier { _, _ -> true } // the pinned certificate replaces hostname verification
                    conn.instanceFollowRedirects = false
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 5_000
                    conn.readTimeout = 8_000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(JSONObject().put("claim", claim).toString().toByteArray()) }
                    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                    TransportResponse(conn.responseCode, stream?.use { String(it.readNBytesCompat(MAX_BODY)) }.orEmpty())
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(512)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_BODY = 2_048
    }
}

/** Turns a pairing offer into a saved-ready [PairingConfig]: the hub swaps the one-time claim for this phone's own token. */
class PairingClient(private val transport: PairingTransport = HttpsPairingTransport()) {
    sealed interface Outcome {
        data class Paired(val config: PairingConfig) : Outcome

        /** The hub answered but refused: the QR code was already used, is too old, or came from another hub. */
        data object Refused : Outcome

        /** No answer: wrong network, hub off, or the certificate is not the one in the link. */
        data object Unreachable : Outcome

        /** An answer we cannot use. */
        data object Invalid : Outcome
    }

    fun redeem(offer: PairingOffer): Outcome {
        val r = transport.redeem(offer.baseUrl, offer.certSha256, offer.claim) ?: return Outcome.Unreachable
        if (r.status in 400..499) return Outcome.Refused
        if (r.status != 200) return Outcome.Invalid
        val token = try { JSONObject(r.body).optString("token") } catch (e: JSONException) { return Outcome.Invalid }
        val config = PairingConfig.parse(offer.baseUrl, token, offer.certSha256) ?: return Outcome.Invalid
        return Outcome.Paired(config)
    }
}
