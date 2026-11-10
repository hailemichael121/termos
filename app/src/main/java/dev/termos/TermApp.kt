package dev.termos

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.core.content.ContextCompat

class TermApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        // Registered once for the whole process: locks even when no Activity exists.
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    Session.shell.onScreenOff()
                    // With "Display over other apps" allowed, come to the front so waking the phone shows TermOS's lock.
                    if (Config.autolock(c) && Settings.canDrawOverlays(c)) {
                        try {
                            c.startActivity(
                                Intent(c, MainActivity::class.java).addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            )
                        } catch (e: Exception) { /* not allowed on this device */ }
                    }
                }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}
