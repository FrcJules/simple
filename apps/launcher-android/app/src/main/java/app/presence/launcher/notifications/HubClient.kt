package app.presence.launcher.notifications

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** Pushes one event to the hub over the home network. Failures are dropped: a missed notification must never crash the phone. */
class HubClient(private val config: PairingConfig) {
    fun send(json: String): Boolean =
        try {
            val url = URL("${config.baseUrl}/events")
            // A ".local" or other name could resolve to a public address: check what we would really connect to.
            if (!InetAddress.getAllByName(url.host).all { it.isSiteLocalAddress }) {
                false
            } else post(url, json)
        } catch (e: java.io.IOException) {
            false
        } catch (e: RuntimeException) {
            false
        }

    private fun post(url: URL, json: String): Boolean {
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.apply {
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
