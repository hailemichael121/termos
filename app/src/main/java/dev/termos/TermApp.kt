package dev.termos

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class TermApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        // Registered once for the whole process: locks even when no Activity exists.
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) { Session.shell.onScreenOff() }
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}
