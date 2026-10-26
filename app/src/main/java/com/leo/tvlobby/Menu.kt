package com.leo.tvlobby

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MenuOption(val label: String, val icon: Int, val accent: Int, val colors: IntArray? = null, val action: () -> Unit)

class Menu(context: Context, title: String, icon: Int, options: List<MenuOption>) :
    Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    init {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(8), 0, 0, context.dp(8))
            addView(ImageView(context).apply { setImageDrawable(context.getDrawable(icon)) }, LinearLayout.LayoutParams(context.dp(32), context.dp(32)))
            addView(TextView(context).apply {
                text = title
                textSize = 22f
                typeface = Fonts.manrope(context, 700)
                setTextColor(Palette.TEXT)
                setPadding(context.dp(14), 0, 0, 0)
            })
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(context.dp(8), context.dp(10), context.dp(8), context.dp(10))
            clipChildren = false
            clipToPadding = false
            options.forEach { addView(tile(it)) }
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
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                clipChildren = false
                clipToPadding = false
                addView(row)
            })
        })
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.6f)
    }

    private fun tile(option: MenuOption) = Tile(context, 200, 72, option.accent).apply {
        title.text = option.label
        detail.visibility = View.GONE
        showMark(context.getDrawable(option.icon)!!, 26)
        option.colors?.let(::fillWith)
        setOnClickListener {
            dismiss()
            option.action()
        }
    }

    companion object {
        const val DANGER = 0xFFE5484D.toInt()
    }
}
