package app.presence.launcher.sms

class FakeGateway(var rows: List<ProviderSms> = emptyList(), private val writable: Boolean = true) : SmsProviderGateway {
    data class Inserted(val address: String, val body: String, val outgoing: Boolean)

    val inserted = mutableListOf<Inserted>()
    val types = mutableMapOf<Long, Int>()
    private var nextId = 100L

    override fun insertIncoming(address: String, body: String, dateMs: Long, dateSentMs: Long, subId: Int): Long? {
        if (!writable) return null
        inserted += Inserted(address, body, false)
        return nextId++
    }

    override fun insertOutgoing(address: String, body: String, dateMs: Long, subId: Int): Long? {
        if (!writable) return null
        inserted += Inserted(address, body, true)
        return nextId++
    }

    override fun setType(providerId: Long, type: Int) {
        types[providerId] = type
    }

    override fun readAll(): List<ProviderSms> = rows

    override fun readSince(afterId: Long): List<ProviderSms> = rows.filter { it.id > afterId }
}
