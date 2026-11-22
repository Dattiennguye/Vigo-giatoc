package vn.vigo.nepnha.reminder

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Tình trạng các quyền ảnh hưởng tới nhắc lịch, và lối tắt tới cài đặt tương ứng (kể cả Xiaomi/HyperOS). */
data class ReminderHealth(
    val notificationsAllowed: Boolean,
    val exactAlarmAllowed: Boolean,
    val ignoringBatteryOptimization: Boolean,
    val isXiaomiFamily: Boolean,
) {
    val allGood: Boolean get() = notificationsAllowed && exactAlarmAllowed && ignoringBatteryOptimization
}

object DeviceHealth {

    fun isXiaomiFamily(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "redmi", "poco").any { it in m }
    }

    fun check(context: Context, scheduler: ReminderScheduler): ReminderHealth {
        val pm = context.getSystemService(PowerManager::class.java)
        return ReminderHealth(
            notificationsAllowed = NotificationHelper.canPost(context),
            exactAlarmAllowed = scheduler.canScheduleExact(),
            ignoringBatteryOptimization = pm?.isIgnoringBatteryOptimizations(context.packageName) == true,
            isXiaomiFamily = isXiaomiFamily(),
        )
    }

    private fun tryStart(context: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun openNotificationSettings(context: Context): Boolean {
        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else appDetails(context)
        return tryStart(context, i, appDetails(context))
    }

    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return tryStart(
            context,
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)),
            appDetails(context),
        )
    }

    fun openBatterySettings(context: Context): Boolean = tryStart(
        context,
        // Màn tiết kiệm pin riêng của MIUI/HyperOS (chọn "Không giới hạn")
        Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", "Nếp Nhà"),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetails(context),
    )

    fun openAutostartSettings(context: Context): Boolean = tryStart(
        context,
        Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
        appDetails(context),
    )

    fun openAppDetails(context: Context): Boolean = tryStart(context, appDetails(context))
}
