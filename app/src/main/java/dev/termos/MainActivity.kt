package dev.termos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var screen: TextView
    private lateinit var scroll: ScrollView
    private lateinit var prompt: TextView
    private lateinit var input: EditText
    private lateinit var buttons: List<Button>
    private lateinit var shell: Shell

    // vim state
    private var vimNormal = false
    private var searching = false
    private var pendingG = false
    private var lastSearch = ""
    private var searchPos = -1
    private var hIdx = -1

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Config.autolock(this@MainActivity) && shell.mode == Shell.Mode.NORMAL) shell.lock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        status = findViewById(R.id.status)
        screen = findViewById(R.id.screen)
        scroll = findViewById(R.id.scroll)
        prompt = findViewById(R.id.prompt)
        input = findViewById(R.id.input)
        buttons = listOf(R.id.btnEsc, R.id.btnUp, R.id.btnDown, R.id.btnTab).map { findViewById<Button>(it) }

        shell = Shell(this, lifecycleScope, ::print, { screen.text = "" }, ::refresh)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* launcher: back does nothing */ }
        })

        input.setOnEditorActionListener { _, _, _ -> submit(); true }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!vimActive()) return
                val t = s?.toString().orEmpty()
                if (t.isEmpty()) return
                input.setText("")
                t.forEach { normalKey(it) }
            }
        })

        buttons[0].setOnClickListener { esc() }
        buttons[1].setOnClickListener { arrow(-1) }
        buttons[2].setOnClickListener { arrow(1) }
        buttons[3].setOnClickListener { tab() }

        shell.start()
        refresh()
    }

    override fun onResume() { super.onResume(); input.requestFocus() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); input.requestFocus() }
    override fun onDestroy() { unregisterReceiver(screenOff); super.onDestroy() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) { esc(); return true }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------ input
    private fun vimActive() = Config.vim(this) && vimNormal && !searching && !shell.secret

    private fun submit() {
        val line = input.text.toString()
        input.setText("")
        hIdx = -1
        if (searching) {
            searching = false; lastSearch = line; searchPos = -1
            search(1); refresh(); return
        }
        print(if (shell.secret) shell.prompt() else shell.prompt() + line)
        shell.onLine(line)
        refresh()
    }

    private fun esc() {
        if (shell.secret) return
        input.setText("")
        if (Config.vim(this)) { vimNormal = true; searching = false; pendingG = false }
        refresh()
    }

    private fun arrow(dir: Int) {
        if (vimNormal) { scroll.smoothScrollBy(0, dir * screen.lineHeight * 2); return }
        val h = shell.historyList()
        if (h.isEmpty() || shell.secret) return
        hIdx = if (dir < 0) { if (hIdx < 0) h.size - 1 else maxOf(0, hIdx - 1) }
        else { if (hIdx < 0) -1 else hIdx + 1 }
        if (hIdx < 0 || hIdx >= h.size) { hIdx = -1; input.setText("") }
        else { input.setText(h[hIdx]); input.setSelection(input.text.length) }
    }

    private fun tab() {
        if (vimNormal || shell.secret) return
        val t = input.text.toString()
        val c = shell.complete(t)
        when {
            c.isEmpty() -> {}
            c.size == 1 -> { input.setText(c[0] + " "); input.setSelection(input.text.length) }
            else -> {
                print(c.joinToString("  "))
                val common = c.reduce { a, b -> a.commonPrefixWith(b) }
                if (common.length > t.length) { input.setText(common); input.setSelection(common.length) }
            }
        }
    }

    // -------------------------------------------------------------- vim keys
    private fun normalKey(c: Char) {
        val line = screen.lineHeight
        when (c) {
            'j' -> scroll.smoothScrollBy(0, line)
            'k' -> scroll.smoothScrollBy(0, -line)
            'd' -> scroll.smoothScrollBy(0, scroll.height / 2)
            'u' -> scroll.smoothScrollBy(0, -scroll.height / 2)
            'G' -> scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            'g' -> {
                if (pendingG) { scroll.post { scroll.fullScroll(View.FOCUS_UP) }; pendingG = false }
                else pendingG = true
                return
            }
            'i', 'a', 'o', ':' -> vimNormal = false
            '/' -> searching = true
            'n' -> search(1)
            'N' -> search(-1)
        }
        pendingG = false
        refresh()
    }

    private fun search(dir: Int) {
        val q = lastSearch.lowercase()
        if (q.isEmpty()) return
        val text = screen.text.toString().lowercase()
        val idx = if (dir > 0)
            text.indexOf(q, searchPos + 1).takeIf { it >= 0 } ?: text.indexOf(q)
        else
            text.lastIndexOf(q, searchPos - 1).takeIf { it >= 0 } ?: text.lastIndexOf(q)
        if (idx < 0) { status.text = "not found: $lastSearch"; return }
        searchPos = idx
        screen.layout?.let { l -> scroll.smoothScrollTo(0, l.getLineTop(l.getLineForOffset(idx))) }
    }

    // ----------------------------------------------------------------- state
    private fun applyTheme() {
        val t = Themes.get(Config.theme(this)) ?: Themes.all[0]
        root.setBackgroundColor(t.bg)
        screen.setTextColor(t.fg)
        prompt.setTextColor(t.accent)
        input.setTextColor(t.input)
        status.setBackgroundColor(t.bar)
        status.setTextColor(t.accent)
        buttons.forEach { it.setTextColor(t.accent); it.setBackgroundColor(t.bar) }
        val sz = Config.font(this).toFloat()
        screen.textSize = sz; prompt.textSize = sz; input.textSize = sz
        window.statusBarColor = t.bg
        window.navigationBarColor = t.bg
        val light = ColorUtils.calculateLuminance(t.bg) > 0.5
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun refresh() {
        val vim = Config.vim(this)
        if (shell.secret || !vim) { vimNormal = false; searching = false }
        applyTheme()
        prompt.text = when { searching -> "/"; vimNormal -> ""; else -> shell.prompt() }
        status.text = when {
            searching -> "-- SEARCH --  type text, Enter"
            vim && vimNormal -> "-- NORMAL --  j k d u gg G  / n N  i=insert"
            vim && !shell.secret -> "-- INSERT --  ESC=normal"
            shell.secret -> "TermOS $VERSION  [locked]"
            else -> "TermOS $VERSION"
        }
        val type = if (shell.secret)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if (input.inputType != type) input.inputType = type
        if (!vimNormal) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun print(s: String) {
        screen.append(s + "\n")
        if (screen.text.length > 60000) screen.text = screen.text.subSequence(screen.text.length - 40000, screen.text.length)
        if (!vimNormal) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
