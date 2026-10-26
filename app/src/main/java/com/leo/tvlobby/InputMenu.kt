package com.leo.tvlobby

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class InputMenu(context: Context, input: Input, onOpen: () -> Unit, onTurnOff: () -> Unit) :
    Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    init {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(8), 0, 0, context.dp(8))
            addView(ImageView(context).apply { setImageDrawable(context.getDrawable(input.mark)) }, LinearLayout.LayoutParams(context.dp(32), context.dp(32)))
            addView(TextView(context).apply {
                text = input.label
                textSize = 22f
                typeface = Fonts.manrope(context, 700)
                setTextColor(Palette.TEXT)
                setPadding(context.dp(14), 0, 0, 0)
            })
        }
        val options = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(context.dp(8), context.dp(10), 0, context.dp(10))
            clipChildren = false
            clipToPadding = false
            addView(option("Open", R.drawable.icon_input, input.colors.last(), onOpen).apply { fillWith(input.colors) })
            addView(option("Turn off", R.drawable.icon_power, TURN_OFF_RED, onTurnOff))
        }
        setContentView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = context.dp(24).toFloat()
                setColor(0xF2161922.toInt())
            }
            setPadding(context.dp(24), context.dp(24), context.dp(10), context.dp(16))
            clipChildren = false
            clipToPadding = false
            addView(header)
            addView(options)
        })
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.6f)
    }

    private fun option(label: String, icon: Int, accent: Int, action: () -> Unit) = Tile(context, 200, 72, accent).apply {
        title.text = label
        detail.visibility = View.GONE
        showMark(context.getDrawable(icon)!!, 26)
        setOnClickListener {
            dismiss()
            action()
        }
    }

    private companion object {
        const val TURN_OFF_RED = 0xFFE5484D.toInt()
    }
}
