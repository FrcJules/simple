package app.presence.launcher.notifications

import java.net.InetAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Pushes one event to the hub over the home network, over TLS pinned to the paired certificate. Failures are dropped: a missed notification must never crash the phone. */
class HubClient(private val config: PairingConfig) {
    fun send(json: String): Boolean =
        try {
            val base = URL(config.baseUrl)
            // A ".local" or other name could resolve to a public address: check what we would really connect to, and connect to
            // exactly that address (resolved once, so it cannot change in between), never through a proxy.
            val addresses = InetAddress.getAllByName(base.host)
            if (addresses.isEmpty() || !addresses.all { it.isSiteLocalAddress }) {
                false
            } else post(URL("https", addresses.first().hostAddress, base.port, "/events"), json)
        } catch (e: java.io.IOException) {
            false
        } catch (e: RuntimeException) {
            false
        }

    private fun post(url: URL, json: String): Boolean {
        val conn = url.openConnection(Proxy.NO_PROXY) as HttpsURLConnection
        return try {
            conn.apply {
                sslSocketFactory = CertificatePin.sslContext(config.certSha256).socketFactory
                setHostnameVerifier { _, _ -> true } // the pinned certificate replaces hostname verification
                instanceFollowRedirects = false // never forward the token or message to another host
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 5_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.token}")
            }
            conn.outputStream.use { it.write(json.toByteArray()) }
            conn.responseCode == 202
        } finally {
            conn.disconnect()
        }
    }
}
