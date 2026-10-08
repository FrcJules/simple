package app.presence.launcher.notifications

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/** What a pairing QR code offers: where the hub is, a one-time secret to redeem, and the certificate to expect. The claim is never logged. */
data class PairingOffer(val baseUrl: String, val claim: String, val certSha256: String) {
    override fun toString() = "PairingOffer(baseUrl=$baseUrl)"

    /** The fingerprint in groups of four, to compare by eye with the one the hub prints. */
    fun groupedFingerprint(): String = certSha256.chunked(4).joinToString(" ")

    /** The same, on eight lines of two groups: large text must not break a group in the middle. */
    fun fingerprintLines(): List<String> = fingerprintLines(certSha256)

    companion object {
        fun fingerprintLines(sha256: String): List<String> = sha256.chunked(4).chunked(2).map { it.joinToString(" ") }
    }
}

/**
 * The link shown as a QR code by the home display (`npm run pair` on the hub): `presence://pair?u=<address>&s=<one-time claim>&f=<fingerprint>`.
 * It carries **no long-lived secret**: only a claim valid for two minutes and usable once, which the phone redeems over a connection
 * pinned to the certificate in the link. Anything can open a `presence://` link, so it is never trusted on its own: it only fills the
 * settings, which show the host and fingerprint for the family to compare with the hub's own display before anything is saved.
 */
object PairingLink {
    const val MAX_LENGTH = 400
    private val claimChars = Regex("[A-Za-z0-9_-]{16,200}")

    fun parse(link: String?): PairingOffer? {
        if (link == null || link.length > MAX_LENGTH) return null
        val uri = try { URI(link) } catch (e: URISyntaxException) { return null }
        if (uri.scheme != "presence" || uri.host != "pair") return null
        val params = HashMap<String, String>()
        for (pair in (uri.rawQuery ?: return null).split('&')) {
            val i = pair.indexOf('=')
            if (i <= 0) return null
            val key = decode(pair.substring(0, i)) ?: return null
            val value = decode(pair.substring(i + 1)) ?: return null
            if (key in params) return null // the same field twice is ambiguous: refuse
            params[key] = value
        }
        if (params.keys != setOf("u", "s", "f")) return null // exactly these three, nothing else
        val claim = params.getValue("s")
        if (!claimChars.matches(claim)) return null
        return PairingOffer(
            baseUrl = PairingConfig.normalizeAddress(params.getValue("u")) ?: return null,
            claim = claim,
            certSha256 = CertificatePin.normalize(params.getValue("f")) ?: return null,
        )
    }

    private fun decode(s: String): String? =
        try { URLDecoder.decode(s, "UTF-8") } catch (e: IllegalArgumentException) { null }
}
