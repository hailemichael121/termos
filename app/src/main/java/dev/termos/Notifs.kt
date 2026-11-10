package dev.termos

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

class TermNotifListener : NotificationListenerService() {
    override fun onListenerConnected() { Notifs.service = this; Notifs.refresh(this) }
    override fun onListenerDisconnected() { if (Notifs.service === this) Notifs.service = null }
    override fun onNotificationPosted(sbn: StatusBarNotification) { Notifs.refresh(this); Notifs.onPosted(this, sbn) }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { Notifs.refresh(this) }
    override fun onDestroy() { if (Notifs.service === this) Notifs.service = null; super.onDestroy() }
}

object Notifs {
    @Volatile var service: NotificationListenerService? = null
    data class Item(val key: String, val app: String, val title: String, val text: String, val time: Long, val canReply: Boolean)
    @Volatile var items: List<Item> = emptyList()
    @Volatile private var sbns: Map<String, StatusBarNotification> = emptyMap()

    fun enabled(c: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    fun refresh(s: NotificationListenerService) {
        try {
            val list = s.activeNotifications.filter { it.isClearable && it.packageName != s.packageName }.sortedByDescending { it.postTime }
            sbns = list.associateBy { it.key }
            items = list.map { toItem(s, it) }
        } catch (e: Exception) { items = emptyList(); sbns = emptyMap() }
    }

    private fun hasReply(n: Notification): Boolean = n.actions?.any { a -> a.remoteInputs?.isNotEmpty() == true } == true

    private fun toItem(c: Context, n: StatusBarNotification): Item {
        val ex = n.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        val app = try {
            c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(n.packageName, 0)).toString()
        } catch (e: Exception) { n.packageName }
        return Item(n.key, app, title, text, n.postTime, hasReply(n.notification))
    }

    fun onPosted(c: Context, n: StatusBarNotification) {
        if (Store.get(c, "notifwatch") != "on" || n.packageName == c.packageName || !n.isClearable) return
        if (Session.shell.mode != Shell.Mode.NORMAL) return
        val item = toItem(c, n)
        Session.echo("[notif] ${item.app}: ${item.title} ${item.text}".take(160))
    }

    fun open(key: String): Boolean = try { sbns[key]?.notification?.contentIntent?.send(); sbns[key] != null } catch (e: Exception) { false }

    fun clear(key: String?) {
        val s = service ?: return
        if (key == null) s.cancelAllNotifications() else s.cancelNotification(key)
    }

    fun reply(c: Context, key: String, text: String): Boolean {
        val n = sbns[key]?.notification ?: return false
        val act = n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true } ?: return false
        val inputs = act.remoteInputs
        val i = Intent()
        val b = Bundle()
        b.putCharSequence(inputs[0].resultKey, text)
        RemoteInput.addResultsToIntent(inputs, i, b)
        return try { act.actionIntent.send(c, 0, i); true } catch (e: Exception) { false }
    }
}
