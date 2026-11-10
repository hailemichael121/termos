package dev.termos

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.io.File

object Notifier {
    const val CH = "termos_alerts"
    const val CH_KEEP = "termos_keepalive"

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "TermOS alerts", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_KEEP, "TermOS running", NotificationManager.IMPORTANCE_MIN))
    }

    fun canPost(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openIntent(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun post(c: Context, title: String, text: String) {
        if (!canPost(c)) return
        ensureChannels(c)
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openIntent(c))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { NotificationManagerCompat.from(c).notify((System.currentTimeMillis() % 1_000_000).toInt(), n) } catch (e: SecurityException) { }
    }

    @Suppress("DEPRECATION")
    fun buzz(c: Context) {
        try {
            (c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                .vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }
}

object Alarms {
    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pi(c: Context, kind: String, id: Int, label: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction("dev.termos.ALARM")
            .putExtra("kind", kind).putExtra("id", id).putExtra("label", label)
        return PendingIntent.getBroadcast(c, (kind + id).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun exactOk(c: Context): Boolean = Build.VERSION.SDK_INT < 31 || am(c).canScheduleExactAlarms()

    fun set(c: Context, kind: String, id: Int, label: String, atMs: Long) {
        val a = am(c)
        val p = pi(c, kind, id, label)
        if (exactOk(c)) a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
        else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
    }

    fun cancel(c: Context, kind: String, id: Int) { am(c).cancel(pi(c, kind, id, "")) }
}

data class TimerRec(val id: Int, val end: Long, val label: String)

object Timers {
    private fun f(c: Context) = File(c.filesDir, "timers.txt")

    fun load(c: Context): List<TimerRec> =
        f(c).takeIf { it.exists() }?.readLines()?.mapNotNull { l ->
            val p = l.split('\t', limit = 3)
            val id = p.getOrNull(0)?.toIntOrNull()
            val e = p.getOrNull(1)?.toLongOrNull()
            if (p.size < 3 || id == null || e == null) null else TimerRec(id, e, p[2])
        } ?: emptyList()

    fun save(c: Context, l: List<TimerRec>) =
        f(c).writeText(l.joinToString("\n") { "${it.id}\t${it.end}\t${it.label}" } + if (l.isEmpty()) "" else "\n")

    fun nextId(c: Context) = (load(c).maxOfOrNull { it.id } ?: 0) + 1
    fun add(c: Context, t: TimerRec) = save(c, load(c) + t)
    fun remove(c: Context, id: Int) = save(c, load(c).filter { it.id != id })
}

object Jobs {
    private fun f(c: Context) = File(c.filesDir, "jobs.txt")

    fun load(c: Context): List<SchedJob> =
        f(c).takeIf { it.exists() }?.readLines()?.mapNotNull { SchedJob.parse(it) } ?: emptyList()

    fun save(c: Context, l: List<SchedJob>) =
        f(c).writeText(l.joinToString("\n") { it.serialize() } + if (l.isEmpty()) "" else "\n")

    fun nextId(c: Context) = (load(c).maxOfOrNull { it.id } ?: 0) + 1
    fun add(c: Context, j: SchedJob) = save(c, load(c) + j)
    fun remove(c: Context, id: Int) = save(c, load(c).filter { it.id != id })

    fun fire(c: Context, id: Int) {
        val j = load(c).firstOrNull { it.id == id } ?: return
        val next = Schedule.nextAfter(j, System.currentTimeMillis())
        if (next == null) remove(c, id)
        else {
            val nj = j.copy(next = next)
            save(c, load(c).map { if (it.id == id) nj else it })
            Alarms.set(c, "job", id, "", next)
        }
        Session.shell.runHeadless(j.cmd)
    }

    fun rescheduleAll(c: Context) {
        val now = System.currentTimeMillis()
        val keep = mutableListOf<SchedJob>()
        for (j in load(c)) {
            var nj = j
            if (j.next <= now) {
                val n = Schedule.nextAfter(j, now) ?: continue
                nj = j.copy(next = n)
            }
            keep.add(nj)
            Alarms.set(c, "job", nj.id, "", nj.next)
        }
        save(c, keep)
        val t = System.currentTimeMillis()
        Timers.load(c).filter { it.end > t }.forEach { Alarms.set(c, "timer", it.id, it.label, it.end) }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        when (i.getStringExtra("kind")) {
            "timer" -> {
                val label = i.getStringExtra("label") ?: ""
                Timers.remove(c, id)
                Notifier.post(c, "TermOS timer", if (label.isEmpty()) "Timer #$id is done" else label)
                Notifier.buzz(c)
                Session.background("timer #$id done" + (if (label.isEmpty()) "" else ": $label"))
            }
            "job" -> Jobs.fire(c, id)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Jobs.rescheduleAll(c) }
}

class LockReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Session.shell.lock() }
}

class KeepAliveService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        val lock = PendingIntent.getBroadcast(this, 1, Intent(this, LockReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Notifier.CH_KEEP)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("TermOS is running")
            .setContentText("keeps timers, jobs and auto-lock reliable")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(Notifier.openIntent(this))
            .addAction(0, "Lock now", lock)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(1, n)
        } catch (e: Exception) { stopSelf() }
        return START_STICKY
    }
}
