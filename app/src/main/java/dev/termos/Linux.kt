package dev.termos

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Bridge to Termux (RUN_COMMAND intent). Needs allow-external-apps=true in Termux. */
object Linux {
    const val TERMUX = "com.termux"
    const val PERM = "com.termux.permission.RUN_COMMAND"

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(TERMUX, 0); true
    } catch (e: Exception) { false }

    fun hasPerm(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, PERM) == PackageManager.PERMISSION_GRANTED

    /** Single-quote a string for bash. */
    fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Returns an error message, or null on success. */
    fun run(ctx: Context, cmd: String): String? {
        val i = Intent()
            .setClassName(TERMUX, "com.termux.app.RunCommandService")
            .setAction("com.termux.RUN_COMMAND")
            .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", cmd))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        return try { ctx.startForegroundService(i); null } catch (e: Exception) { e.message ?: "failed" }
    }
}
