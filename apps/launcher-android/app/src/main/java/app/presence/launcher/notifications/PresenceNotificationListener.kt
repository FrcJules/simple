package app.presence.launcher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Watches whitelisted apps only; everything else is dropped by [NotificationParser] without being read. */
class PresenceNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!NotificationParser.isWatched(sbn.packageName)) return // before reading any content
        if (sbn.notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0) return
        val config = PairingStore(applicationContext).load() ?: return
        val extras = sbn.notification.extras
        val raw = RawNotification(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            category = sbn.notification.category,
        )
        val incoming = NotificationParser.parse(raw) ?: return
        val json = EventJson.build(incoming, UUID.randomUUID().toString(), EventJson.timestamp(sbn.postTime))
        executor.execute { HubClient(config).send(json) }
    }

    private companion object {
        // Bounded queue: if the hub is offline, old messages are dropped instead of piling up in memory.
        val executor = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.SECONDS, ArrayBlockingQueue(20), ThreadPoolExecutor.DiscardOldestPolicy(),
        )
    }
}
