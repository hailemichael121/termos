package dev.termos

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * Process-lifetime owner of the shell and the output buffer.
 * The Activity only attaches listeners in onStart and detaches them in onStop,
 * so rotating/recreating the UI never leaks it and never loses the session.
 */
object Session {
    private lateinit var app: Application
    private var scope: CoroutineScope? = null
    lateinit var shell: Shell
        private set
    private val lines = ArrayDeque<String>()

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

    /** Test hook: wipe all state; optionally start with a password already set (LOGIN mode). */
    fun resetForTest(password: String? = null) {
        listOf("termos", "termos_cfg").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        listOf("notes.txt", "todo.txt", "history.txt", "aliases.txt").forEach { File(app.filesDir, it).delete() }
        if (password != null) Auth.setPassword(app, password)
        clearListener?.invoke()
        create()
        stateListener?.invoke()
    }
}
