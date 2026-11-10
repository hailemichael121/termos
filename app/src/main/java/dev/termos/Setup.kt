package dev.termos

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

object Setup {
    enum class Id(val key: String, val title: String, val why: String, val action: String, val neutral: String) {
        HOME("home", "Default launcher",
            "Make TermOS your home screen so the Home button opens the terminal.", "Choose", "Never ask"),
        LOCK("lock", "Screen lock",
            "Android's own lock screen is on, so you unlock twice. Set Screen lock to None (or Swipe) and TermOS's password becomes your only lock.",
            "Open settings", "Keep both locks"),
        BATTERY("battery", "Unrestricted battery",
            "Stops the system from killing TermOS, so timers, scheduled jobs and the auto-lock keep working.", "Allow", "Never ask"),
        NOTIF("notifications", "Notifications",
            "Lets TermOS show timer and job alerts and its small 'running' notice. (If no popup appears, enable them in app settings.)", "Allow", "Never ask"),
        ALARMS("alarms", "Exact alarms",
            "Makes timers and scheduled jobs fire on time instead of a few minutes late.", "Allow", "Never ask"),
        OVERLAY("overlay", "Lock over apps",
            "When the screen turns off, TermOS comes to the front, so waking the phone shows the TermOS password instead of the app you left open.", "Allow", "Never ask"),
        NOTIFACCESS("notifs", "Notification access", "Lets the 'notifs' command read, open and reply to notifications.", "Enable", "Never ask"),
        CONTACTS("contacts", "Contacts", "Lets 'contacts', 'call <name>' and 'sms' use your contact names.", "Allow", "Never ask"),
        SMS("sms", "SMS", "Lets the 'sms' command read and send text messages.", "Allow", "Never ask")
    }

    val startup = listOf(Id.HOME, Id.LOCK, Id.BATTERY, Id.NOTIF, Id.ALARMS, Id.OVERLAY)

    fun isHome(c: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = c.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == c.packageName
    }

    fun ok(c: Context, id: Id): Boolean = when (id) {
        Id.HOME -> isHome(c)
        Id.LOCK -> !(c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        Id.BATTERY -> (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
        Id.NOTIF -> Notifier.canPost(c)
        Id.ALARMS -> Alarms.exactOk(c)
        Id.OVERLAY -> Settings.canDrawOverlays(c)
        Id.NOTIFACCESS -> Notifs.enabled(c)
        Id.CONTACTS -> Comms.has(c, Comms.P_CONTACTS)
        Id.SMS -> Comms.has(c, Comms.P_READ_SMS) && Comms.has(c, Comms.P_SEND_SMS)
    }

    fun skipped(c: Context, id: Id) = Store.get(c, "skip.${id.key}") == "1"
    fun pending(c: Context, id: Id) = !ok(c, id) && !skipped(c, id)

    fun table(c: Context): String =
        Id.values().joinToString("\n") {
            val st = if (ok(c, it)) "ok" else if (skipped(c, it)) "skipped" else "MISSING"
            "%-13s %-8s %s".format(it.key, st, it.title)
        } + "\n\nfix: perms <name> | perms all (opens the first missing item)"

    private fun go(c: Context, i: Intent) { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    /** Opens the right settings screen / permission popup. Returns a hint to print. */
    fun fix(c: Context, id: Id, requestPerm: (String) -> Boolean): String {
        val pkg = Uri.parse("package:${c.packageName}")
        return try {
            when (id) {
                Id.HOME -> {
                    try { go(c, Intent(Settings.ACTION_HOME_SETTINGS)) }
                    catch (e: Exception) { go(c, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
                    "pick TermOS as the Home app."
                }
                Id.LOCK -> { go(c, Intent(Settings.ACTION_SECURITY_SETTINGS)); "set Screen lock to None (or Swipe)." }
                Id.BATTERY -> {
                    go(c, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
                    "choose Allow.\n" + Oem.tip(Build.MANUFACTURER)
                }
                Id.NOTIF -> {
                    go(c, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName))
                    "turn notifications on for TermOS."
                }
                Id.ALARMS ->
                    if (Build.VERSION.SDK_INT >= 31) { go(c, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)); "allow 'Alarms & reminders'." }
                    else "not needed on this Android version."
                Id.OVERLAY -> { go(c, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)); "allow 'Display over other apps' for TermOS." }
                Id.NOTIFACCESS -> { go(c, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); "enable TermOS in the list." }
                Id.CONTACTS ->
                    if (requestPerm(Manifest.permission.READ_CONTACTS)) "allow the popup." else "allow Contacts for TermOS in Android app settings."
                Id.SMS ->
                    if (requestPerm("android.permission.READ_SMS,android.permission.SEND_SMS")) "allow the popup." else "allow SMS for TermOS in Android app settings."
            }
        } catch (e: Exception) { "could not open settings: ${e.message}" }
    }
}

object KeepAlive {
    fun ensure(c: Context) { if (Config.keepalive(c)) start(c) }
    fun start(c: Context) {
        try { ContextCompat.startForegroundService(c, Intent(c, KeepAliveService::class.java)) } catch (e: Exception) { /* not allowed right now */ }
    }
    fun stop(c: Context) { try { c.stopService(Intent(c, KeepAliveService::class.java)) } catch (e: Exception) { } }
}

/** Shows the setup dialogs on open. Holds the Activity only as long as the Activity lives; dialog is dismissed in onStop. */
class SetupPrompter(private val act: Activity) {
    private var dialog: AlertDialog? = null
    private val day = 24L * 3600 * 1000

    private fun allowed(m: Shell.Mode) = m == Shell.Mode.NORMAL || m == Shell.Mode.LOGIN || m == Shell.Mode.SET1
    private fun off() = Store.get(act, "setup_prompts") == "off"

    fun onResume(m: Shell.Mode) {
        if (off()) return
        KeepAlive.ensure(act)
        maybePrompt(m)
    }

    fun dismiss() { dialog?.dismiss(); dialog = null }

    fun maybePrompt(m: Shell.Mode) {
        if (off() || !allowed(m) || act.isFinishing || dialog?.isShowing == true) return
        val now = System.currentTimeMillis()
        val id = Setup.startup.firstOrNull {
            Setup.pending(act, it) && now - (Store.get(act, "asked.${it.key}")?.toLongOrNull() ?: 0L) > day
        } ?: return
        show(id)
    }

    private fun mark(id: Setup.Id) = Store.put(act, "asked.${id.key}", System.currentTimeMillis().toString())

    private fun show(id: Setup.Id) {
        val extra = if (id == Setup.Id.BATTERY) "\n\n" + Oem.tip(Build.MANUFACTURER) else ""
        val d = AlertDialog.Builder(act)
            .setTitle(id.title)
            .setMessage(id.why + extra)
            .setPositiveButton(id.action) { _, _ -> mark(id); fix(id) }
            .setNegativeButton("Later") { _, _ -> mark(id) }
            .setNeutralButton(id.neutral) { _, _ -> Store.put(act, "skip.${id.key}", "1") }
            .create()
        d.setOnDismissListener { if (dialog === d) dialog = null }
        dialog = d
        d.show()
    }

    private fun fix(id: Setup.Id) {
        when {
            id == Setup.Id.HOME && Build.VERSION.SDK_INT >= 29 -> {
                val rm = act.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 21)
                } else Setup.fix(act, id) { false }
            }
            id == Setup.Id.NOTIF && Build.VERSION.SDK_INT >= 33 ->
                act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
            else -> Setup.fix(act, id) { p -> act.requestPermissions(p.split(',').toTypedArray(), 13); true }
        }
    }
}
