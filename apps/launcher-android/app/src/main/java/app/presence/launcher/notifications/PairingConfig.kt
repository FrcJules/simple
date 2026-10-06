package app.presence.launcher.notifications

import java.net.URI

/** Where and how to reach the family hub. Only https home-network addresses with a pinned certificate are accepted. */
data class PairingConfig(val baseUrl: String, val token: String, val certSha256: String) {
    /** Keeps the token out of logs. */
    override fun toString() = "PairingConfig(baseUrl=$baseUrl)"

    companion object {
        const val MIN_TOKEN_LENGTH = 16

        /** Returns null unless: https URL on a home-network address, token long enough, valid SHA-256 certificate fingerprint. */
        fun parse(url: String, token: String, fingerprint: String): PairingConfig? {
            val uri = try { URI(url.trim()) } catch (e: java.net.URISyntaxException) { return null }
            val host = uri.host ?: return null
            if (uri.scheme != "https") return null
            val pin = CertificatePin.normalize(fingerprint) ?: return null
            if (!isLocalNetworkHost(host) || token.trim().length < MIN_TOKEN_LENGTH) return null
            return PairingConfig(url.trim().trimEnd('/'), token.trim(), pin)
        }

        fun isLocalNetworkHost(host: String): Boolean {
            val h = host.lowercase()
            if (h.endsWith(".local")) return true
            val p = h.split('.').map { it.toIntOrNull() ?: return false }
            if (p.size != 4 || p.any { it !in 0..255 }) return false
            return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
        }
    }
}
