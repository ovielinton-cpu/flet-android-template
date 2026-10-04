package {{ cookiecutter.org_name_2 }}.{{ cookiecutter.package_name }}

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {

    private fun listenerComponent() = ComponentName(this, NotificationCaptureService::class.java)

    // True only when "Notification access" is switched ON for this app.
    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones like Honor/Huawei quietly disconnect the bank-alert listener. Switching the
        // listener component off and on again forces Android to reconnect it - the same as
        // turning Notification access OFF/ON by hand, but done for you every time the app opens.
        if (hasNotificationAccess()) {
            try {
                val pm = packageManager
                pm.setComponentEnabledSetting(listenerComponent(),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(listenerComponent(),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            } catch (e: Exception) { }
        }
    }

    override fun onResume() {
        super.onResume()
        // Every time you come back to the app, also ask Android to reconnect the listener.
        if (hasNotificationAccess()) {
            try {
                NotificationListenerService.requestRebind(listenerComponent())
            } catch (e: Exception) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Flet can start its Python engine only once per process. If Android closes this
        // screen but keeps the process alive, the next launch shows a blank page until
        // Force stop. Ending the process here makes every reopen a clean start.
        // (Screen rotation is not affected: isChangingConfigurations is true then.)
        if (!isChangingConfigurations) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
