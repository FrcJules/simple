package app.simple.launcher.notifications

import java.net.URI
import java.net.URISyntaxException

/** Where and how to reach the family hub. Only https home-network addresses with a pinned certificate are accepted. */
data class PairingConfig(val baseUrl: String, val token: String, val certSha256: String) {
    /** Keeps the token out of logs. */
    override fun toString() = "PairingConfig(baseUrl=$baseUrl)"

    companion object {
        const val MIN_TOKEN_LENGTH = 16

        /** Returns null unless: https URL on a home-network address, token long enough, valid SHA-256 certificate fingerprint. */
        fun parse(url: String, token: String, fingerprint: String): PairingConfig? {
            val base = normalizeAddress(url) ?: return null
            val pin = CertificatePin.normalize(fingerprint) ?: return null
            if (token.trim().length < MIN_TOKEN_LENGTH) return null
            return PairingConfig(base, token.trim(), pin)
        }

        /**
         * `https://host[:port]`, **rebuilt from the parsed parts**: what is stored and shown is the host that will really be contacted.
         * Credentials, a path, a query or a fragment in the address are refused (they can hide the real host behind a familiar prefix).
         */
        fun normalizeAddress(url: String): String? {
            val uri = try { URI(url.trim()) } catch (e: URISyntaxException) { return null }
            if (uri.scheme?.lowercase() != "https" || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            val path = uri.rawPath.orEmpty()
            if (path != "" && path != "/") return null
            val host = uri.host?.lowercase() ?: return null
            if (!isLocalNetworkHost(host)) return null
            val port = uri.port
            if (port != -1 && port !in 1..65535) return null
            return "https://$host" + if (port != -1) ":$port" else ""
        }

        private val octet = Regex("0|[1-9][0-9]{0,2}") // no leading zeros: some resolvers read "010" as octal (= 8)
        private val mdnsName = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*\\.local")

        fun isLocalNetworkHost(host: String): Boolean {
            val h = host.lowercase()
            if (h.endsWith(".local")) return mdnsName.matches(h)
            val parts = h.split('.')
            if (parts.size != 4 || parts.any { !octet.matches(it) }) return false
            val p = parts.map { it.toInt() }
            if (p.any { it > 255 }) return false
            return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
        }
    }
}
