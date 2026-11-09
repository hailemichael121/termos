package dev.termos

import android.content.Context
import android.content.Intent

data class AppInfo(val label: String, val pkg: String)

class AppIndex(private val ctx: Context) {
    var last: List<AppInfo> = emptyList()
        private set

    fun all(): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun list(filter: String): List<AppInfo> {
        val f = filter.lowercase()
        last = all().filter { f.isEmpty() || it.label.lowercase().contains(f) || it.pkg.contains(f) }
        return last
    }

    /** Accepts a number from the last `ls`, an exact name, or a partial name. */
    fun resolve(q: String): AppInfo? {
        q.toIntOrNull()?.let { return last.getOrNull(it - 1) }
        val all = all()
        val exact = all.filter { it.label.equals(q, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact.first()
        return all.firstOrNull { it.label.lowercase().contains(q.lowercase()) }
    }
}
