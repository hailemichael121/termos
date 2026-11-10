package dev.termos

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Typeface
import android.text.style.StyleSpan
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thin view layer. All state lives in [Session]; this class attaches listeners in
 * onStart and detaches them in onStop so it can never be leaked or lose the session.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var screen: TextView
    private lateinit var scroll: ScrollView
    private lateinit var prompt: TextView
    private lateinit var input: EditText
    private lateinit var buttons: List<Button>
    private lateinit var lockHud: LinearLayout
    private lateinit var lockClock: TextView
    private lateinit var lockBattery: TextView
    private lateinit var lockEmblem: ImageView
    private lateinit var lockSub: TextView
    private val shell: Shell get() = Session.shell
    private lateinit var prompter: SetupPrompter
    private lateinit var keyboardPanel: LinearLayout
    private lateinit var keyboardFab: Button
    private lateinit var termKeyboard: TermKeyboard

    private var vimNormal = false
    private var searching = false
    private var pendingG = false
    private var lastSearch = ""
    private var searchPos = -1
    private var hIdx = -1
    private var statusBase = ""
    private var renderedTheme = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_main)
        prompter = SetupPrompter(this)
        root = findViewById(R.id.root)
        status = findViewById(R.id.status)
        screen = findViewById(R.id.screen)
        scroll = findViewById(R.id.scroll)
        prompt = findViewById(R.id.prompt)
        input = findViewById(R.id.input)
        buttons = listOf(R.id.btnEsc, R.id.btnUp, R.id.btnDown, R.id.btnTab, R.id.btnCtrlC, R.id.btnPipe)
            .map { findViewById<Button>(it) }
        lockHud = findViewById(R.id.lockHud)
        lockClock = findViewById(R.id.lockClock)
        lockBattery = findViewById(R.id.lockBattery)
        lockEmblem = findViewById(R.id.lockEmblem)
        lockSub = findViewById(R.id.lockSub)
        keyboardPanel = findViewById(R.id.keyboardPanel)
        keyboardFab = findViewById(R.id.keyboardFab)
        termKeyboard = TermKeyboard(
            act = this,
            panel = keyboardPanel,
            fab = keyboardFab,
            input = input,
            shell = { shell },
            onSubmit = { submit() },
            onTab = { tab() },
            onCtrlC = { shell.interrupt(); refresh() },
        )

        val labels = listOf("Escape", "Previous command", "Next command", "Complete", "Cancel", "Pipe")
        buttons.forEachIndexed { i, b -> b.contentDescription = labels[i] }
        input.contentDescription = "Command input"
        input.showSoftInputOnFocus = false
        input.setOnFocusChangeListener { _, _ -> hideSystemKeyboard() }
        screen.contentDescription = "Terminal output"
        screen.setOnClickListener { focusInput() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (keyboardPanel.visibility == View.VISIBLE) {
                    termKeyboard.setCollapsed(true)
                    return
                }
                /* launcher: back does nothing else */
            }
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
        buttons[4].setOnClickListener { shell.interrupt(); refresh() }
        buttons[5].setOnClickListener { input.text.insert(input.selectionStart.coerceAtLeast(0), "|") }

        // status-bar clock: lifecycle-bound, stops when the UI is not visible
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    renderStatus()
                    renderLockHud()
                    val locked = shell.secret && Look.lockStyle(this@MainActivity) != "terminal"
                    delay(if (locked) 1_000 else 30_000)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Session.sink = { appendText(it) }
        Session.clearListener = { screen.text = "" }
        Session.stateListener = { refresh() }
        shell.permRequester = { requestPermissions(it.split(',').toTypedArray(), 11) }
        renderedTheme = ""
        refresh()
    }

    override fun onStop() {
        Store.put(this, "last_active", System.currentTimeMillis().toString())
        prompter.dismiss(); detach(); super.onStop()
    }
    override fun onDestroy() { detach(); super.onDestroy() }

    private fun detach() {
        Session.sink = null; Session.clearListener = null; Session.stateListener = null
        shell.permRequester = null
    }

    override fun onResume() { super.onResume(); focusInput(); prompter.onResume(shell.mode) }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); focusInput() }

    private fun hideSystemKeyboard() {
        WindowCompat.getInsetsController(window, input).hide(WindowInsetsCompat.Type.ime())
    }

    /** Cursor in the prompt; only the in-app TermOS keyboard is used (never the system IME). */
    private fun focusInput() {
        input.requestFocus()
        hideSystemKeyboard()
        termKeyboard.syncVisibility()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) focusInput()
    }

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
        if (searching) { searching = false; lastSearch = line; searchPos = -1; search(1); refresh(); return }
        Session.echo(if (shell.secret) shell.prompt() else shell.prompt() + Parser.redact(line))
        if (!shell.secret) WordCache.remember(this, line)
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
        hIdx = if (dir < 0) { if (hIdx < 0) h.size - 1 else maxOf(0, hIdx - 1) } else { if (hIdx < 0) -1 else hIdx + 1 }
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
                Session.echo(c.joinToString("  "))
                val common = c.reduce { a, b -> a.commonPrefixWith(b) }
                if (common.length > t.length) { input.setText(common); input.setSelection(common.length) }
            }
        }
    }

    // --------------------------------------------------------------- vim keys
    private fun normalKey(c: Char) {
        val line = screen.lineHeight
        when (c) {
            'j' -> scroll.smoothScrollBy(0, line)
            'k' -> scroll.smoothScrollBy(0, -line)
            'd' -> scroll.smoothScrollBy(0, scroll.height / 2)
            'u' -> scroll.smoothScrollBy(0, -scroll.height / 2)
            'G' -> scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            'g' -> {
                if (pendingG) { scroll.post { scroll.fullScroll(View.FOCUS_UP) }; pendingG = false } else pendingG = true
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
        val idx = if (dir > 0) text.indexOf(q, searchPos + 1).takeIf { it >= 0 } ?: text.indexOf(q)
                  else text.lastIndexOf(q, searchPos - 1).takeIf { it >= 0 } ?: text.lastIndexOf(q)
        if (idx < 0) { statusBase = "not found: $lastSearch"; renderStatus(); return }
        searchPos = idx
        screen.layout?.let { l -> scroll.smoothScrollTo(0, l.getLineTop(l.getLineForOffset(idx))) }
    }

    // ---------------------------------------------------------------- render
    private fun theme() = Themes.get(Config.theme(this)) ?: Themes.all[0]

    private fun errColor(t: Theme) =
        if (ColorUtils.calculateLuminance(t.bg) > 0.5) 0xFFB00020.toInt() else 0xFFFF6B6B.toInt()

    private fun roleColor(role: String, t: Theme): Int = Look.color(this, role) ?: when (role) {
        "user" -> t.accent
        "input" -> t.input
        "output" -> t.fg
        "error" -> errColor(t)
        "dim" -> ColorUtils.setAlphaComponent(t.fg, 0x99)
        "banner" -> t.accent
        "time" -> ColorUtils.setAlphaComponent(t.fg, 0xBB)
        else -> t.fg
    }

    private fun styleFlag(role: String): Int = when (Look.style(this, role)) {
        TxtStyle.BOLD -> Typeface.BOLD
        TxtStyle.ITALIC -> Typeface.ITALIC
        TxtStyle.BOLDITALIC -> Typeface.BOLD_ITALIC
        TxtStyle.NORMAL -> Typeface.NORMAL
    }

    private fun typefaceFor(role: String): Typeface = Typeface.create(Typeface.MONOSPACE, styleFlag(role))

    private fun paint(sb: SpannableStringBuilder, from: Int, to: Int, role: String, t: Theme) {
        if (from >= to) return
        sb.setSpan(ForegroundColorSpan(roleColor(role, t)), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val f = styleFlag(role)
        if (f != Typeface.NORMAL) sb.setSpan(StyleSpan(f), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun styled(line: String): CharSequence {
        val t = theme()
        if (line.isNotEmpty() && (line[0] == Banner.MARK || line[0] == Banner.TMARK)) {
            val sb = SpannableStringBuilder(line.substring(1) + "\n")
            paint(sb, 0, sb.length, if (line[0] == Banner.MARK) "banner" else "time", t)
            return sb
        }
        val sb = SpannableStringBuilder(line + "\n")
        when (LineStyle.classify(line)) {
            Kind.PROMPT -> {
                val cut = LookFmt.promptEnd(line)
                paint(sb, 0, cut, "user", t)
                paint(sb, cut, sb.length, "input", t)
            }
            Kind.ERROR -> paint(sb, 0, sb.length, "error", t)
            Kind.DIM -> paint(sb, 0, sb.length, "dim", t)
            Kind.NORMAL -> paint(sb, 0, sb.length, "output", t)
        }
        return sb
    }

    private fun appendText(s: String) {
        val sb = SpannableStringBuilder()
        s.split("\n").forEach { sb.append(styled(it)) }
        screen.append(sb)
        (screen.text as? Editable)?.let { if (it.length > 60000) it.delete(0, 20000) }
        if (!vimNormal) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun renderAll() {
        screen.text = ""
        val sb = SpannableStringBuilder()
        Session.snapshot().forEach { sb.append(styled(it)) }
        screen.append(sb)
    }

    private fun applyTheme() {
        val t = theme()
        root.setBackgroundColor(t.bg)
        screen.setTextColor(roleColor("output", t))
        prompt.setTextColor(roleColor("user", t))
        input.setTextColor(roleColor("input", t))
        screen.typeface = typefaceFor("output")
        prompt.typeface = typefaceFor("user")
        input.typeface = typefaceFor("input")
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
        if (::termKeyboard.isInitialized) termKeyboard.applyTheme()
    }

    private fun batteryPct(): Int =
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun renderStatus() {
        val bat = batteryPct()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        status.text = if (shell.secret && Look.lockStyle(this) != "terminal") statusBase
                      else "$statusBase  |  $time  bat $bat%"
    }

    private fun renderLockHud() {
        val style = Look.lockStyle(this)
        if (!shell.secret || style == "terminal") {
            lockHud.visibility = View.GONE
            scroll.visibility = View.VISIBLE
            return
        }
        lockHud.visibility = View.VISIBLE
        val t = theme()
        val accent = roleColor("time", t)
        lockClock.setTextColor(accent)
        lockBattery.setTextColor(accent)
        lockSub.setTextColor(ColorUtils.setAlphaComponent(roleColor("dim", t), 0xCC))
        val sz = Look.lockFontSize(this).toFloat()
        lockClock.textSize = sz
        lockBattery.textSize = (sz * 0.48f).coerceAtLeast(14f)
        val clockPat = if (sz >= 38) "HH:mm" else "EEE  HH:mm"
        lockClock.text = SimpleDateFormat(clockPat, Locale.getDefault()).format(Date())
        lockBattery.text = "battery ${batteryPct()}%"
        lockEmblem.visibility = if (Look.lockShowEmblem(this)) View.VISIBLE else View.GONE
        val den = resources.displayMetrics.density
        val emblemDp = when (style) {
            "emblem" -> 140
            "minimal" -> 96
            else -> 72
        }
        val lp = lockEmblem.layoutParams
        val px = (emblemDp * den).toInt()
        lp.width = px
        lp.height = px
        lockEmblem.layoutParams = lp
        scroll.visibility = if (style == "minimal" || style == "emblem") View.GONE else View.VISIBLE
        val sub = Look.sub(this)
        if (sub.isNotEmpty()) {
            lockSub.text = sub
            lockSub.visibility = View.VISIBLE
        } else lockSub.visibility = View.GONE
    }

    private fun refresh() {
        val vim = Config.vim(this)
        if (shell.secret || !vim) { vimNormal = false; searching = false }
        applyTheme()
        val sig = Config.theme(this) + "|" + Look.signature(this)
        if (sig != renderedTheme) { renderedTheme = sig; renderAll() }
        prompt.text = when { searching -> "/"; vimNormal -> ""; else -> shell.prompt() }
        statusBase = when {
            searching -> "-- SEARCH --  type text, Enter"
            vim && vimNormal -> "-- NORMAL --  j k d u gg G  / n N  i=insert"
            vim && !shell.secret -> "-- INSERT --  ESC=normal"
            shell.secret -> Look.title(this) + "  [locked]"
            else -> Look.title(this)
        }
        renderStatus()
        val type = if (shell.secret) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        if (input.inputType != type) input.inputType = type
        input.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_DONE
        hideSystemKeyboard()
        prompter.maybePrompt(shell.mode)
        renderLockHud()
        termKeyboard.onLockChanged()
        termKeyboard.syncVisibility()
        if (!vimNormal) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
