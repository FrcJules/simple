package app.presence.launcher.notifications

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Builds the JSON expected by the hub (`packages/contracts`, schemaVersion 1). */
object EventJson {
    fun build(incoming: Incoming, id: String, occurredAt: String): String {
        val o = JSONObject().put("schemaVersion", 1).put("id", id).put("occurredAt", occurredAt)
        when (incoming) {
            is Incoming.MissedCall -> o.put("type", "missed_call").put("from", person(incoming.from))
            is Incoming.Message -> o.put("type", "new_message").put("from", person(incoming.from))
                .put("channel", incoming.channel.wire).put("text", incoming.text)
        }
        return o.toString()
    }

    /** ISO-8601 with offset and seconds, as the hub requires. */
    fun timestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    private fun person(name: String) = JSONObject().put("name", name)
}
