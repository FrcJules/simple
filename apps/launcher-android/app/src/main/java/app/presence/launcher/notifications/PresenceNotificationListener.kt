package app.presence.launcher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.UUID
import java.util.concurrent.Executors

/** Watches whitelisted apps only; everything else is dropped by [NotificationParser] without being read. */
class PresenceNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
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
        val executor = Executors.newSingleThreadExecutor()
    }
}
