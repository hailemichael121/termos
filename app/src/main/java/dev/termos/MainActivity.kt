package dev.termos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {
    private lateinit var screen: TextView
    private lateinit var scroll: ScrollView
    private lateinit var prompt: TextView
    private lateinit var input: EditText
    private lateinit var shell: Shell

    // Auto-lock the terminal whenever the screen turns off
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (shell.mode == Shell.Mode.NORMAL) shell.lock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        screen = findViewById(R.id.screen)
        scroll = findViewById(R.id.scroll)
        prompt = findViewById(R.id.prompt)
        input = findViewById(R.id.input)

        shell = Shell(this, lifecycleScope, ::print, { screen.text = "" }, ::refresh)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))

        // A launcher must not "finish" on back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op */ }
        })

        input.setOnEditorActionListener { _, _, _ -> submit(); true }
        shell.start()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        input.requestFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        input.requestFocus()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    private fun submit() {
        val line = input.text.toString()
        input.setText("")
        print(if (shell.secret) shell.prompt() else shell.prompt() + line)
        shell.onLine(line)
        refresh()
    }

    private fun refresh() {
        prompt.text = shell.prompt()
        input.inputType = if (shell.secret)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun print(s: String) {
        screen.append(s + "\n")
        if (screen.text.length > 60000) {
            screen.text = screen.text.subSequence(screen.text.length - 40000, screen.text.length)
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
