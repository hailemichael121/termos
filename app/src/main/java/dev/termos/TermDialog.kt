package dev.termos

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/** Terminal-themed modal; matches the active colour theme instead of Material defaults. */
class TermDialog(private val act: Activity) {
    private var dialog: Dialog? = null

    fun isShowing() = dialog?.isShowing == true

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    fun show(
        title: String,
        message: String,
        positive: String,
        negative: String = "Later",
        neutral: String? = null,
        onPositive: () -> Unit,
        onNegative: () -> Unit = {},
        onNeutral: (() -> Unit)? = null,
        onCancel: () -> Unit = onNegative
    ) {
        dismiss()
        val t = Themes.get(Config.theme(act)) ?: Themes.all[0]
        val view = LayoutInflater.from(act).inflate(R.layout.dialog_termos, null)
        val root = view.findViewById<LinearLayout>(R.id.dlgRoot)
        val dlgTitle = view.findViewById<TextView>(R.id.dlgTitle)
        val dlgMessage = view.findViewById<TextView>(R.id.dlgMessage)
        val dlgPositive = view.findViewById<Button>(R.id.dlgPositive)
        val dlgNegative = view.findViewById<Button>(R.id.dlgNegative)
        val dlgNeutral = view.findViewById<Button>(R.id.dlgNeutral)
        val accent = Look.color(act, "user") ?: t.accent
        val fg = Look.color(act, "output") ?: t.fg
        val border = ColorUtils.setAlphaComponent(accent, 0x88)
        val radius = 10f * act.resources.displayMetrics.density
        root.background = GradientDrawable().apply {
            setColor(t.bg)
            setStroke((1.5f * act.resources.displayMetrics.density).toInt().coerceAtLeast(1), border)
            cornerRadius = radius
        }
        dlgTitle.setTextColor(accent)
        dlgTitle.text = title
        dlgMessage.setTextColor(fg)
        dlgMessage.text = message
        listOf(dlgPositive, dlgNegative, dlgNeutral).forEach { b ->
            b.setTextColor(accent)
            b.setBackgroundColor(t.bar)
        }
        dlgPositive.text = positive
        dlgNegative.text = negative
        if (neutral != null && onNeutral != null) {
            dlgNeutral.visibility = View.VISIBLE
            dlgNeutral.text = neutral
            dlgNeutral.setOnClickListener { onNeutral(); dismiss() }
        } else dlgNeutral.visibility = View.GONE
        val d = Dialog(act, R.style.TermDialog)
        d.setContentView(view)
        d.setCancelable(true)
        d.setOnCancelListener { onCancel(); dismiss() }
        dlgPositive.setOnClickListener { onPositive(); dismiss() }
        dlgNegative.setOnClickListener { onNegative(); dismiss() }
        dialog = d
        d.show()
        val w = d.window ?: return
        val pad = (act.resources.displayMetrics.density * 28).toInt()
        w.setLayout(act.resources.displayMetrics.widthPixels - pad, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
