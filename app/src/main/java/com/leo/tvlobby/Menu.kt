package com.leo.tvlobby

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MenuOption(
    val label: String,
    val icon: Drawable,
    val accent: Int,
    val colors: IntArray? = null,
    val active: Boolean = false,
    val action: () -> Unit,
)

class MenuRow(val label: String?, val options: List<MenuOption>)

class Menu(context: Context, title: CharSequence, icon: Drawable, rows: List<MenuRow>) :
    Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    init {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(8), 0, 0, context.dp(8))
            addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(context.dp(32), context.dp(32)))
            addView(TextView(context).apply {
                text = title
                textSize = 22f
                typeface = Fonts.manrope(context, 700)
                setTextColor(Palette.TEXT)
                setPadding(context.dp(14), 0, 0, 0)
            })
        }
        val firstTiles = mutableListOf<View>()
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
            rows.forEach { row ->
                row.label?.let { addView(rowLabel(it)) }
                val tiles = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(context.dp(8), context.dp(10), context.dp(8), context.dp(10))
                    clipChildren = false
                    clipToPadding = false
                    row.options.forEach { addView(tile(it)) }
                }
                tiles.getChildAt(0)?.let(firstTiles::add)
                addView(HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    clipChildren = false
                    clipToPadding = false
                    addView(tiles)
                })
            }
        })
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.6f)
        setOnShowListener { firstTiles.firstOrNull()?.requestFocus() }
    }

    private fun rowLabel(text: String) = TextView(context).apply {
        this.text = text.uppercase()
        textSize = 12f
        typeface = Fonts.manrope(context, 700)
        letterSpacing = 0.14f
        setTextColor(Palette.MUTED)
        setPadding(context.dp(8), context.dp(8), 0, 0)
    }

    private fun tile(option: MenuOption) = Tile(context, 200, 72, option.accent).apply {
        title.text = option.label
        detail.visibility = View.GONE
        showMark(option.icon, 26)
        option.colors?.let(::fillWith)
        active = option.active
        setOnClickListener {
            dismiss()
            option.action()
        }
    }

    companion object {
        const val DANGER = 0xFFE5484D.toInt()
    }
}
