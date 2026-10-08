package app.presence.launcher.notifications

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * The link shown as a QR code by the home display (`npm run pair` on the hub): `presence://pair?u=<address>&t=<token>&f=<fingerprint>`.
 * Anything can open a `presence://` link, so it is checked exactly like typed values ([PairingConfig.parse]: https, home network,
 * token length, certificate fingerprint) and it only **fills in** the settings: nothing is saved until the family confirms.
 */
object PairingLink {
    const val MAX_LENGTH = 600

    fun parse(link: String?): PairingConfig? {
        if (link == null || link.length > MAX_LENGTH) return null
        val uri = try { URI(link) } catch (e: URISyntaxException) { return null }
        if (uri.scheme != "presence" || uri.host != "pair") return null
        val params = HashMap<String, String>()
        for (pair in (uri.rawQuery ?: return null).split('&')) {
            val i = pair.indexOf('=')
            if (i <= 0) continue
            val key = decode(pair.substring(0, i)) ?: return null
            val value = decode(pair.substring(i + 1)) ?: return null
            if (key in params) return null // the same field twice is ambiguous: refuse
            params[key] = value
        }
        return PairingConfig.parse(params["u"] ?: return null, params["t"] ?: return null, params["f"] ?: return null)
    }

    private fun decode(s: String): String? =
        try { URLDecoder.decode(s, "UTF-8") } catch (e: IllegalArgumentException) { null }
}
