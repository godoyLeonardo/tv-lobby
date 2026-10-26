package com.leo.tvlobby

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

object Palette {
    const val BACKGROUND = 0xFF0D0E12.toInt()
    const val SURFACE = 0xFF1B1D24.toInt()
    const val TEXT = 0xFFF2F3F5.toInt()
    const val MUTED = 0xFF8A8F98.toInt()
    const val ON = 0xFF3DDC84.toInt()
    const val STANDBY = 0xFFFFB020.toInt()
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun blend(from: Int, to: Int, ratio: Float): Int {
    fun mix(a: Int, b: Int) = (a + (b - a) * ratio).roundToInt()
    return Color.rgb(mix(Color.red(from), Color.red(to)), mix(Color.green(from), Color.green(to)), mix(Color.blue(from), Color.blue(to)))
}

class Tile(context: Context, widthDp: Int, heightDp: Int, private val accent: Int) : FrameLayout(context) {
    val title = TextView(context).apply {
        textSize = 19f
        setTextColor(Palette.TEXT)
        typeface = Fonts.manrope(context, 700)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val detail = TextView(context).apply {
        textSize = 13f
        typeface = Fonts.manrope(context, 500)
        maxLines = 1
    }

    private val fill = GradientDrawable().apply {
        cornerRadius = context.dp(14).toFloat()
        orientation = GradientDrawable.Orientation.TL_BR
    }
    private var gradient: IntArray? = null
    private val ring = GradientDrawable().apply { cornerRadius = context.dp(14).toFloat() }
    private val dotFill = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val dot = View(context).apply {
        background = dotFill
        visibility = GONE
    }
    private val text = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        addView(detail)
        addView(title)
    }

    var active = false
        set(value) {
            field = value
            render()
        }

    init {
        layoutParams = LinearLayout.LayoutParams(context.dp(widthDp), context.dp(heightDp)).apply { marginEnd = context.dp(18) }
        isFocusable = true
        isClickable = true
        background = fill
        foreground = ring
        clipToOutline = true
        addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(dot, LayoutParams(context.dp(10), context.dp(10), Gravity.TOP or Gravity.END).apply {
            setMargins(0, context.dp(14), context.dp(14), 0)
        })
        setOnFocusChangeListener { _, hasFocus ->
            val scale = if (hasFocus) 1.07f else 1f
            animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) context.dp(8).toFloat() else 0f).setDuration(120).start()
            render()
        }
        render()
    }

    fun showStatus(color: Int?) {
        dot.visibility = if (color == null) GONE else VISIBLE
        color?.let(dotFill::setColor)
    }

    fun showImage(image: Drawable) {
        text.visibility = GONE
        addView(ImageView(context).apply {
            setImageDrawable(image)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun showMark(mark: Drawable, colors: IntArray) {
        gradient = colors
        detail.visibility = GONE
        text.gravity = Gravity.CENTER_VERTICAL
        text.setPadding(context.dp(80), 0, context.dp(16), 0)
        addView(ImageView(context).apply { setImageDrawable(mark) }, LayoutParams(context.dp(44), context.dp(44), Gravity.CENTER_VERTICAL or Gravity.START).apply {
            marginStart = context.dp(22)
        })
        render()
    }

    fun showIcon(icon: Drawable, label: CharSequence) {
        title.text = label
        title.textSize = 15f
        detail.visibility = GONE
        addView(ImageView(context).apply { setImageDrawable(icon) }, LayoutParams(context.dp(36), context.dp(36), Gravity.TOP or Gravity.START).apply {
            setMargins(context.dp(16), context.dp(12), 0, 0)
        })
    }

    private fun render() {
        val colors = gradient
        if (colors != null) fill.colors = colors else fill.setColor(blend(Palette.SURFACE, accent, if (active) 0.6f else 0.25f))
        when {
            isFocused -> ring.setStroke(context.dp(3), Palette.TEXT)
            active -> ring.setStroke(context.dp(2), accent)
            else -> ring.setStroke(0, Color.TRANSPARENT)
        }
        detail.setTextColor(if (active) Palette.TEXT else Palette.MUTED)
    }
}
