package app.presence.launcher.notifications

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class EventJsonTest {
    @Test
    fun missedCallMatchesHubContract() {
        val o = JSONObject(EventJson.build(Incoming.MissedCall("Marie"), "e1", "2026-10-06T09:30:00+02:00"))
        assertEquals(1, o.getInt("schemaVersion"))
        assertEquals("missed_call", o.getString("type"))
        assertEquals("Marie", o.getJSONObject("from").getString("name"))
    }

    @Test
    fun messageMatchesHubContract() {
        val o = JSONObject(EventJson.build(Incoming.Message("Jules", Channel.WHATSAPP, "Salut"), "e2", "2026-10-06T09:30:00+02:00"))
        assertEquals("new_message", o.getString("type"))
        assertEquals("whatsapp", o.getString("channel"))
        assertEquals("Salut", o.getString("text"))
    }

    @Test
    fun timestampAlwaysHasSecondsAndOffset() =
        // 2026-10-06T07:30:00Z: the seconds field must still be printed
        assertEquals("2026-10-06T09:30:00+02:00", EventJson.timestamp(1_791_271_800_000L, ZoneId.of("Europe/Paris")))
}
