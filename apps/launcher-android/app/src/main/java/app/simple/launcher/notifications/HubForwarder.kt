package app.simple.launcher.notifications

import android.content.Context
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Sends an event to the paired home display, if there is one. Shared by the notification listener and the SMS receiver. */
object HubForwarder {
    // Bounded queue: if the hub is offline, old messages are dropped instead of piling up in memory.
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, ArrayBlockingQueue(20), ThreadPoolExecutor.DiscardOldestPolicy())

    fun forward(context: Context, incoming: Incoming, atMs: Long) {
        val config = PairingStore(context.applicationContext).load() ?: return
        val json = EventJson.build(incoming, UUID.randomUUID().toString(), EventJson.timestamp(atMs))
        executor.execute { HubClient(config).send(json) }
    }
}
