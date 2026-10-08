package app.presence.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingClientTest {
    private val offer = PairingOffer("https://192.168.1.66:8788", "claim-0123456789abcdef", "88A2899B9DF00490B76854FB11FF4F1019470BA0D5FB7CD68BC700E4B4B7B68F")

    private class Fake(val response: TransportResponse?) : PairingTransport {
        var seen: Triple<String, String, String>? = null
        override fun redeem(baseUrl: String, certSha256: String, claim: String): TransportResponse? {
            seen = Triple(baseUrl, certSha256, claim)
            return response
        }
    }

    private fun outcome(r: TransportResponse?) = PairingClient(Fake(r)).redeem(offer)

    @Test
    fun aGoodAnswerGivesAConfigWithTheHubsAddressAndPinnedCertificate() {
        val o = outcome(TransportResponse(200, """{"token":"device-token-0123456789abcdef0123"}""")) as PairingClient.Outcome.Paired
        assertEquals("https://192.168.1.66:8788", o.config.baseUrl)
        assertEquals("device-token-0123456789abcdef0123", o.config.token)
        assertEquals(offer.certSha256, o.config.certSha256)
    }

    @Test
    fun theRequestGoesToTheAddressAndCertificateOfTheOffer() {
        val t = Fake(TransportResponse(200, """{"token":"device-token-0123456789abcdef0123"}"""))
        PairingClient(t).redeem(offer)
        assertEquals(Triple(offer.baseUrl, offer.certSha256, offer.claim), t.seen)
    }

    @Test
    fun aRefusalIsNotTheSameAsNoAnswer() {
        assertEquals(PairingClient.Outcome.Refused, outcome(TransportResponse(403, """{"error":"claim_refused"}""")))
        assertEquals(PairingClient.Outcome.TooManyAttempts, outcome(TransportResponse(429, """{"error":"too_many_attempts"}""")))
        assertEquals(PairingClient.Outcome.Unreachable, outcome(null))
    }

    @Test
    fun unusableAnswersAreInvalidNotACrash() {
        assertEquals(PairingClient.Outcome.Invalid, outcome(TransportResponse(500, "")))
        assertEquals(PairingClient.Outcome.Invalid, outcome(TransportResponse(200, "not json")))
        assertEquals(PairingClient.Outcome.Invalid, outcome(TransportResponse(200, """{"nope":1}""")))
        assertEquals(PairingClient.Outcome.Invalid, outcome(TransportResponse(200, """{"token":"short"}""")))
    }

    @Test
    fun theClaimIsNotInTheOfferText() = assertTrue(!offer.toString().contains(offer.claim))
}
