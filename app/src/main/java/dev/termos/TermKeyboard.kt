package dev.termos

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.GestureDetectorCompat

private enum class KeyLayer { LATIN, SYM, AMHARIC }

class TermKeyboard(
    private val act: Activity,
    private val panel: LinearLayout,
    private val fab: Button,
    private val input: EditText,
    private val shell: () -> Shell,
    private val onSubmit: () -> Unit,
    private val onTab: () -> Unit,
    private val onCtrlC: () -> Unit,
) {
    private var shift = false
    private var layer = KeyLayer.LATIN
    private var amFamily = 0
    private var lockFullKeys = false

    private val quickScroll = HorizontalScrollView(act).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        isHorizontalScrollBarEnabled = false
    }
    private val quickRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
    private val keyGrid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val titleBar = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
    }

    private val lower = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m"),
    )
    private val pinRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    private val symRows = listOf(
        listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
        listOf("-", "_", "=", "+", "[", "]", "{", "}", "\\", "|"),
        listOf(";", ":", "'", "\"", ",", ".", "/", "?", "`", "~"),
    )
    /** One row only — pipe/Tab/^C live on the input toolbar above this panel. */
    private val shortcuts = listOf(
        "ls", "clear", "help", "sudo", "open", "theme", "look", "ai", "linux", "lock"
    )

    private val density get() = act.resources.displayMetrics.density
    private val keyMinH get() = (44 * density).toInt()
    private val keyPad get() = (6 * density).toInt()
    private val keyText get() = 15f
    private val actionText get() = 12f

    private fun locked() = shell().secret

    init {
        quickScroll.addView(quickRow)
        panel.removeAllViews()
        panel.orientation = LinearLayout.VERTICAL
        panel.addView(titleBar)
        panel.addView(quickScroll)
        panel.addView(keyGrid)
        val collapse = Button(act).apply {
            text = "▾"
            isAllCaps = false
            typeface = Typeface.MONOSPACE
            textSize = 18f
            minHeight = 0
            minimumHeight = 0
            contentDescription = "Hide keyboard"
            val pad = (6 * density).toInt()
            setPadding(pad, 0, pad, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { setCollapsed(true) }
        }
        titleBar.addView(collapse)
        swipeToDismiss(titleBar)
        swipeToDismiss(panel)
        rebuildKeys()
        updateQuickRow()
        fab.setOnClickListener { openKeyboard() }
        swipeOpenFab(fab)
        fab.text = "⌨"
        fab.contentDescription = "Open TermOS keyboard — tap or swipe up"
        input.addTextChangedListener(SimpleAfter { updateQuickRow() })
        input.showSoftInputOnFocus = false
        amFamily = Store.get(act, "keyboard.am.family")?.toIntOrNull() ?: 0
    }

    private fun openKeyboard() {
        if (locked()) lockFullKeys = false
        setCollapsed(false)
    }

    private fun swipeOpenFab(v: View) {
        val detector = GestureDetectorCompat(act, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(_e1: MotionEvent?, e2: MotionEvent, _vx: Float, vy: Float): Boolean {
                if (vy < -400) { openKeyboard(); return true }
                return false
            }
        })
        v.setOnTouchListener { view, ev ->
            detector.onTouchEvent(ev)
            if (ev.action == MotionEvent.ACTION_UP) view.performClick()
            true
        }
    }

    private fun swipeToDismiss(v: View) {
        val detector = GestureDetectorCompat(act, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(_e1: MotionEvent?, e2: MotionEvent, _vx: Float, vy: Float): Boolean {
                if (vy > 500) { setCollapsed(true); return true }
                return false
            }
        })
        v.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); false }
    }

    fun themeColors(): Triple<Int, Int, Int> {
        val t = Themes.get(Config.theme(act)) ?: Themes.all[0]
        val fg = Look.color(act, "input") ?: t.input
        val accent = Look.color(act, "user") ?: t.accent
        val bar = t.bar
        return Triple(fg, accent, bar)
    }

    fun applyTheme() {
        val (fg, accent, bar) = themeColors()
        panel.setBackgroundColor(bar)
        fab.setTextColor(accent)
        fab.background = circle(bar, accent)
        titleBar.setBackgroundColor(bar)
        (titleBar.getChildAt(0) as? Button)?.setTextColor(accent)
        updateQuickRow()
        rebuildKeys()
    }

    fun syncVisibility() {
        input.showSoftInputOnFocus = false
        val collapsed = Config.keyboardCollapsed(act)
        panel.visibility = if (!collapsed) View.VISIBLE else View.GONE
        fab.visibility = if (collapsed) View.VISIBLE else View.GONE
    }

    fun setCollapsed(c: Boolean) {
        Store.put(act, "keyboard.collapsed", if (c) "1" else "")
        if (!c) rebuildKeys()
        syncVisibility()
        if (!c) input.requestFocus()
    }

    fun onLockChanged() {
        if (locked()) {
            Store.put(act, "keyboard.collapsed", "1")
            lockFullKeys = false
        } else {
            Store.put(act, "keyboard.collapsed", "")
            layer = KeyLayer.LATIN
        }
        rebuildKeys()
        syncVisibility()
    }

    /** Suggestions while typing; otherwise command shortcuts — single scroll row. */
    private fun updateQuickRow() {
        quickRow.removeAllViews()
        if (locked()) return
        val tail = input.text?.toString()?.substringAfterLast(' ', "") ?: ""
        val (_, accent, bar) = themeColors()
        val items = LinkedHashSet<String>()
        if (tail.isNotEmpty()) {
            WordCache.suggest(act, tail, shell().historyList()).forEach { items.add(it) }
        } else {
            shortcuts.forEach { items.add(it) }
        }
        for (w in items) {
            val b = chip(w, accent, bar)
            b.setOnClickListener {
                if (tail.isNotEmpty() && !shortcuts.contains(w)) replaceTail(w)
                else {
                    input.setText("$w ")
                    input.setSelection(input.text.length)
                }
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            quickRow.addView(b)
        }
    }

    private fun replaceTail(word: String) {
        val t = input.text?.toString() ?: ""
        val cut = t.lastIndexOf(' ').let { if (it < 0) "" else t.substring(0, it + 1) }
        input.setText(cut + word)
        input.setSelection(input.text.length)
    }

    private fun cycleLayer() {
        layer = when (layer) {
            KeyLayer.LATIN -> KeyLayer.SYM
            KeyLayer.SYM -> KeyLayer.AMHARIC
            KeyLayer.AMHARIC -> KeyLayer.LATIN
        }
        shift = false
        rebuildKeys()
    }

    private fun layerButtonLabel() = when (layer) {
        KeyLayer.LATIN -> "?123"
        KeyLayer.SYM -> "አማ"
        KeyLayer.AMHARIC -> "abc"
    }

    private fun rebuildKeys() {
        val (_, accent, bar) = themeColors()
        quickScroll.visibility = if (locked()) View.GONE else View.VISIBLE
        keyGrid.removeAllViews()
        when {
            locked() && !lockFullKeys -> buildPin()
            locked() && lockFullKeys -> buildLatin()
            layer == KeyLayer.LATIN -> buildLatin()
            layer == KeyLayer.SYM -> buildSym()
            else -> buildAmharic()
        }
        buildBottomRow(accent, bar)
        for (i in 0 until keyGrid.childCount) styleRow(keyGrid.getChildAt(i), accent, bar)
    }

    private fun buildPin() {
        for (row in pinRows) {
            val line = rowLine()
            for (cell in row) {
                if (cell == "⌫") line.addView(actionKey("⌫") { backspace() })
                else line.addView(letterKey(cell))
            }
            keyGrid.addView(line)
        }
    }

    private fun buildLatin() {
        for ((ri, row) in lower.withIndex()) {
            val line = rowLine()
            for (ch in row) line.addView(letterKey(if (shift) ch.uppercase() else ch))
            if (ri == lower.lastIndex) line.addView(actionKey("⌫") { backspace() })
            keyGrid.addView(line)
        }
    }

    private fun buildSym() {
        for (row in symRows) {
            val line = rowLine()
            row.forEach { line.addView(letterKey(it)) }
            keyGrid.addView(line)
        }
    }

    private fun buildAmharic() {
        for (row in AmharicLayout.rows(amFamily)) {
            val line = rowLine()
            row.forEach { line.addView(letterKey(it)) }
            keyGrid.addView(line)
        }
    }

    private fun buildBottomRow(accent: Int, bar: Int) {
        val bottom = rowLine()
        when {
            locked() && !lockFullKeys -> {
                bottom.addView(actionKey("abc", wide = true) { lockFullKeys = true; rebuildKeys() })
                bottom.addView(actionKey("⏎", wide = true) { onSubmit() })
            }
            locked() && lockFullKeys -> {
                bottom.addView(actionKey("123") { lockFullKeys = false; rebuildKeys() })
                bottom.addView(actionKey(if (shift) "SH" else "Sh") { shift = !shift; rebuildKeys() })
                bottom.addView(actionKey("space", wide = true) { insert(" ") })
                bottom.addView(actionKey("⏎", wide = true) { onSubmit() })
            }
            else -> {
                bottom.addView(actionKey(layerButtonLabel()) { cycleLayer() })
                if (layer == KeyLayer.LATIN) {
                    bottom.addView(actionKey(if (shift) "SH" else "Sh") { shift = !shift; rebuildKeys() })
                }
                if (layer == KeyLayer.AMHARIC) {
                    val lab = AmharicLayout.familyLabel(amFamily) + "›"
                    bottom.addView(actionKey(lab) {
                        amFamily = AmharicLayout.safeIndex(amFamily + 1)
                        Store.put(act, "keyboard.am.family", amFamily.toString())
                        rebuildKeys()
                    })
                }
                bottom.addView(actionKey("space", wide = true) { insert(" ") })
                bottom.addView(actionKey("⏎", wide = true) { onSubmit() })
            }
        }
        keyGrid.addView(bottom)
    }

    private fun rowLine() = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun letterKey(label: String): Button {
        val b = keyButton(label, wide = true)
        b.setOnClickListener {
            insert(label)
            if (shift && layer == KeyLayer.LATIN) { shift = false; rebuildKeys() }
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        return b
    }

    private fun actionKey(label: String, wide: Boolean = false, fn: () -> Unit = {}): Button {
        val b = keyButton(label, wide)
        b.setOnClickListener { fn(); it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
        return b
    }

    private fun insert(s: String) {
        val start = input.selectionStart.coerceAtLeast(0)
        input.text?.insert(start, s)
    }

    private fun backspace() {
        val start = input.selectionStart.coerceAtLeast(0)
        if (start > 0) input.text?.delete(start - 1, start)
    }

    private fun keyButton(label: String, wide: Boolean): Button = Button(act).apply {
        text = label
        isAllCaps = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        typeface = if (label.length == 1 && label[0] > '\u127F') Typeface.DEFAULT else Typeface.MONOSPACE
        textSize = if (label.length > 3) actionText else keyText
        setPadding(keyPad, keyPad, keyPad, keyPad)
        minimumHeight = keyMinH
        minHeight = keyMinH
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, if (wide) 1.15f else 1f).apply {
            val m = (3 * density).toInt()
            marginStart = m; marginEnd = m; topMargin = m; bottomMargin = m
        }
        background = GradientDrawable().apply { cornerRadius = 8f * density; setColor(0x33000000) }
    }

    private fun chip(text: String, accent: Int, bar: Int): Button = Button(act).apply {
        this.text = text
        isAllCaps = false
        isSingleLine = true
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        typeface = Typeface.MONOSPACE
        textSize = 11f
        setTextColor(accent)
        background = GradientDrawable().apply {
            cornerRadius = 8f * density
            setColor(ColorUtils.blendARGB(bar, accent, 0.12f))
            setStroke(1, ColorUtils.setAlphaComponent(accent, 0x55))
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = (4 * density).toInt()
        }
        minimumHeight = (32 * density).toInt()
        setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
    }

    private fun styleKey(v: View, fg: Int, accent: Int, bar: Int) {
        if (v is Button) {
            v.setTextColor(fg)
            (v.background as? GradientDrawable)?.setColor(ColorUtils.blendARGB(bar, accent, 0.10f))
        } else if (v is ViewGroup) styleRow(v, accent, bar)
    }

    private fun styleRow(row: View, accent: Int, bar: Int) {
        val fg = themeColors().first
        if (row is ViewGroup) for (i in 0 until row.childCount) styleKey(row.getChildAt(i), fg, accent, bar)
    }

    private fun circle(bar: Int, accent: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(bar)
        setStroke((2 * density).toInt(), accent)
    }
}

private class SimpleAfter(private val fn: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) { fn() }
}
