package com.leo.tvlobby

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// "Lobby status" on the TV: the Health checks, filled in as they answer.
class HealthDialog(context: Context, private val controls: Controls) :
    Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    private class Row(val icon: ImageView, val detail: TextView)

    private var rows = 0

    private val handler = Handler(Looper.getMainLooper())
    // Two columns: the checks don't fit the TV's height as one list.
    private val columns = List(2) { LinearLayout(context).apply { orientation = LinearLayout.VERTICAL } }

    init {
        setContentView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = context.dp(24).toFloat()
                setColor(0xF2161922.toInt())
            }
            setPadding(context.dp(32), context.dp(24), context.dp(32), context.dp(20))
            addView(TextView(context).apply {
                text = "Lobby status"
                textSize = 24f
                typeface = Fonts.manrope(context, 700)
                setTextColor(Palette.TEXT)
                setPadding(0, 0, 0, context.dp(8))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                columns.forEachIndexed { index, column ->
                    addView(column, LinearLayout.LayoutParams(context.dp(400), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        if (index == 0) marginEnd = context.dp(32)
                    })
                }
            })
        })
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.6f)
        runChecks()
    }

    private fun runChecks() {
        Health(context, controls).checks.forEach { check ->
            val row = addRow(check.name)
            check.run { result ->
                handler.post {
                    row.icon.setImageDrawable(context.icon(if (result.ok) R.drawable.icon_check_circle else R.drawable.icon_error))
                    row.icon.setColorFilter(if (result.ok) Palette.ON else Menu.DANGER)
                    row.detail.text = result.detail
                    row.detail.setTextColor(if (result.ok) Palette.MUTED else Palette.TEXT)
                }
            }
        }
    }

    private fun addRow(name: String): Row {
        val icon = ImageView(context).apply {
            setImageDrawable(context.icon(R.drawable.icon_schedule))
            setColorFilter(Palette.MUTED)
        }
        val detail = TextView(context).apply {
            text = "Checking…"
            textSize = 15f
            typeface = Fonts.manrope(context, 500)
            setTextColor(Palette.MUTED)
            maxLines = 2
        }
        val column = columns[rows++ % columns.size]
        column.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(10), 0, context.dp(10))
            addView(icon, LinearLayout.LayoutParams(context.dp(26), context.dp(26)).apply { marginEnd = context.dp(16) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = name
                    textSize = 18f
                    typeface = Fonts.manrope(context, 700)
                    setTextColor(Palette.TEXT)
                })
                addView(detail)
            })
        })
        return Row(icon, detail)
    }
}
