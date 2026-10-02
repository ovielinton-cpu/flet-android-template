package {{ cookiecutter.org_name_2 }}.{{ cookiecutter.package_name }}

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Listens for notifications system-wide and appends them, as one JSON line each, to
 * notification_inbox.jsonl in the app's own files folder
 * (/storage/emulated/0/Android/data/<package>/files/). The Python side of the app
 * reads that file, decides which ones are bank alerts, and adds them to Money Tracker.
 *
 * Needs only the "Notification access" switch in Android Settings; no storage permission.
 */
class NotificationCaptureService : NotificationListenerService() {

    companion object {
        private const val INBOX_FILE = "notification_inbox.jsonl"
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Phones with aggressive battery managers (Honor, Huawei, Xiaomi...) sometimes cut
        // the connection. Ask Android to reconnect instead of silently going deaf.
        try {
            NotificationListenerService.requestRebind(ComponentName(this, NotificationCaptureService::class.java))
        } catch (e: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return

        // Never capture our own app's notifications.
        if (pkg == applicationContext.packageName) return

        val notification = sbn.notification ?: return

        // Skip "doing work in the background", music players, downloads and the
        // summary card that groups several messages; they are never bank alerts.
        val flags = notification.flags
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras: Bundle = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        // SMS apps (Google Messages etc.) use MessagingStyle: the real message body is
        // in EXTRA_MESSAGES, while EXTRA_TEXT can be just "2 new messages".
        var messagingText = ""
        try {
            val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (msgs != null && msgs.isNotEmpty()) {
                val last = msgs[msgs.size - 1] as? Bundle
                messagingText = last?.getCharSequence("text")?.toString().orEmpty()
            }
        } catch (e: Exception) { }

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }.orEmpty()

        val body = listOf(messagingText, bigText, lines, text).maxByOrNull { it.length }.orEmpty()
        if (title.isBlank() && body.isBlank()) return

        val entry = JSONObject().apply {
            put("package", pkg)
            put("title", title)
            put("text", body)
            put("posted_at", sbn.postTime)
        }
        appendLine(entry.toString())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Intentionally empty: only arriving notifications matter.
    }

    @Synchronized
    private fun appendLine(line: String) {
        try {
            val dir = getExternalFilesDir(null) ?: return
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, INBOX_FILE), true).use {
                it.write((line + "\n").toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // A missed notification must never crash a system-bound listener.
        }
    }
}
