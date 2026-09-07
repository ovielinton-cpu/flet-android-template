package {{ cookiecutter.org_name_2 }}.{{ cookiecutter.package_name }}

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * System-wide notification listener.
 *
 * Design goals:
 *  - No custom permissions beyond the one Android requires for this service
 *    (BIND_NOTIFICATION_LISTENER_SERVICE, granted by the user in Settings, not
 *    a runtime dialog).
 *  - Writes to the app's own external-files directory
 *    (/storage/emulated/0/Android/data/<package>/files/), which needs no
 *    storage permission on any Android version and is trivially readable
 *    from the Flet/Python side of the app using the same known path.
 *  - Filters by an editable allow-list file so you are not hoovering up
 *    every notification on the phone by default once you've configured it.
 *    Until that file exists, it captures everything so you can discover
 *    your banking apps' exact package names.
 */
class NotificationCaptureService : NotificationListenerService() {

    companion object {
        private const val INBOX_FILE = "notification_inbox.jsonl"
        private const val ALLOWLIST_FILE = "allowed_packages.txt"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return

        // Never capture our own app's notifications.
        if (pkg == applicationContext.packageName) return

        val allowlist = readAllowlist()
        if (allowlist.isNotEmpty() && pkg !in allowlist) return

        val extras: Bundle = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = if (bigText.length > text.length) bigText else text

        if (title.isBlank() && body.isBlank()) return

        val entry = JSONObject().apply {
            put("package", pkg)
            put("title", title)
            put("text", body)
            put("posted_at", sbn.postTime)
        }

        appendLine(INBOX_FILE, entry.toString())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Intentionally empty — we only care about notifications arriving.
    }

    private fun readAllowlist(): Set<String> {
        return try {
            val file = File(getExternalFilesDir(null), ALLOWLIST_FILE)
            if (!file.exists()) return emptySet()
            file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun appendLine(fileName: String, line: String) {
        try {
            val dir = getExternalFilesDir(null) ?: return
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file, true).use { it.write((line + "\n").toByteArray()) }
        } catch (e: Exception) {
            // A missed transaction notification shouldn't crash a system-bound listener.
        }
    }
}
