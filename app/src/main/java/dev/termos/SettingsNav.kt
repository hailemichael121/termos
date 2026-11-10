package dev.termos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Opens the right Android settings page (not the generic Security hub when avoidable). */
object SettingsNav {
    private fun launch(c: Context, i: Intent): Boolean = try {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }

    private fun canResolve(c: Context, i: Intent) =
        i.resolveActivity(c.packageManager) != null

    fun openHomeSettings(c: Context): String {
        val tries = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        )
        for (i in tries) if (canResolve(c, i) && launch(c, i)) return "pick TermOS as the Home app."
        return "open Settings -> Apps -> Default apps -> Home app -> TermOS."
    }

  /**
     * Screen lock -> None/Swipe, not the full Security dashboard when the OS exposes a direct page.
     */
    fun openLockScreen(c: Context): String {
        val tries = mutableListOf<Intent>()
        tries.add(Intent("android.settings.LOCK_SCREEN_SETTINGS"))
        tries.add(Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            putExtra(":settings:show_fragment_as_subsetting", true)
            putExtra(":settings:show_fragment", "com.android.settings.security.screenlock.ScreenLockSettings")
        })
        tries.add(Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            putExtra(":settings:show_fragment", "com.android.settings.security.ScreenLockSettings")
        })
        val oem = listOf(
            ComponentName("com.android.settings", "com.android.settings.Settings\$LockScreenSettingsActivity"),
            ComponentName("com.android.settings", "com.android.settings.Settings\$ScreenLockSettingsActivity"),
            ComponentName("com.android.settings", "com.android.settings.password.ScreenLockSettings"),
        )
        for (cn in oem) {
            tries.add(Intent().setComponent(cn))
        }
        tries.add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        for (i in tries) {
            if (canResolve(c, i) && launch(c, i)) {
                return "choose Screen lock -> None or Swipe (not PIN/Pattern)."
            }
        }
        return "could not open Screen lock. try: Settings -> Security & privacy -> Screen lock -> None"
    }

    fun openBatteryExemption(c: Context): String {
        val pkg = android.net.Uri.parse("package:${c.packageName}")
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        if (launch(c, i)) return "choose Allow.\n" + Oem.tip(Build.MANUFACTURER)
        if (launch(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            return "find TermOS and set Unrestricted.\n" + Oem.tip(Build.MANUFACTURER)
        }
        return "Settings -> Battery -> TermOS -> Unrestricted.\n" + Oem.tip(Build.MANUFACTURER)
    }
}
