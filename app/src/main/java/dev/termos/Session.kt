package dev.termos

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * Process-lifetime owner of the shell and the output buffer.
 * The Activity attaches listeners in onStart and detaches them in onStop (no leaks, survives rotation).
 */
object Session {
    private lateinit var app: Application
    private var scope: CoroutineScope? = null
    lateinit var shell: Shell
        private set
    private val lines = ArrayDeque<String>()
    private val held = ArrayDeque<String>()

    var sink: ((String) -> Unit)? = null
    var clearListener: (() -> Unit)? = null
    var stateListener: (() -> Unit)? = null

    fun init(a: Application) { app = a; create() }

    private fun create() {
        scope?.cancel()
        lines.clear()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        shell = Shell(app, s, ::emit, ::clear, { stateListener?.invoke() })
        shell.start()
    }

    private fun emit(s: String) {
        s.split("\n").forEach { lines.addLast(it) }
        while (lines.size > 1500) lines.removeFirst()
        sink?.invoke(s)
    }

    private fun clear() {
        lines.clear()
        clearListener?.invoke()
    }

    fun echo(s: String) = emit(s)
    fun snapshot(): List<String> = lines.toList()

    /** Output from timers, jobs, notifications: shown when unlocked, held (not shown) while locked. */
    fun background(s: String) {
        if (!::shell.isInitialized) return
        if (shell.mode == Shell.Mode.NORMAL) emit(s)
        else { held.addLast(s); while (held.size > 50) held.removeFirst() }
    }

    fun flushHeld() {
        val l = held.toList()
        held.clear()
        l.forEach { emit(it) }
    }

    /** Test hook: wipe all state; optionally start with a password already set (LOGIN mode). */
    fun resetForTest(password: String? = null) {
        listOf("termos", "termos_cfg").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        Store.put(app, "setup_prompts", "off")
        listOf("notes.txt", "todo.txt", "history.txt", "aliases.txt", "timers.txt", "jobs.txt")
            .forEach { File(app.filesDir, it).delete() }
        File(app.filesDir, "scripts").deleteRecursively()
        held.clear()
        if (password != null) Auth.setPassword(app, password)
        clearListener?.invoke()
        create()
        stateListener?.invoke()
    }
}
