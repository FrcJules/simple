package app.presence.launcher.notifications

import java.net.HttpURLConnection
import java.net.URL

/** Pushes one event to the hub over the home network. Failures are dropped: a missed notification must never crash the phone. */
class HubClient(private val config: PairingConfig) {
    fun send(json: String): Boolean =
        try {
            val conn = URL("${config.baseUrl}/events").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer ${config.token}")
                conn.outputStream.use { it.write(json.toByteArray()) }
                conn.responseCode == 202
            } finally {
                conn.disconnect()
            }
        } catch (e: java.io.IOException) {
            false
        }
}
