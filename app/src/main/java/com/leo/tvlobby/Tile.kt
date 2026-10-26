package com.leo.tvlobby

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

object Palette {
    const val BACKGROUND = 0xFF0D0E12.toInt()
    const val SURFACE = 0xFF1B1D24.toInt()
    const val TEXT = 0xFFF2F3F5.toInt()
    const val TEXT_SOFT = 0xD9FFFFFF.toInt()
    const val MUTED = 0xFF8A8F98.toInt()
    const val ON = 0xFF3DDC84.toInt()
    const val STANDBY = 0xFFFFB020.toInt()
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun Context.icon(res: Int): Drawable = getDrawable(res)!!

fun blend(from: Int, to: Int, ratio: Float): Int {
    fun mix(a: Int, b: Int) = (a + (b - a) * ratio).roundToInt()
    return Color.rgb(mix(Color.red(from), Color.red(to)), mix(Color.green(from), Color.green(to)), mix(Color.blue(from), Color.blue(to)))
}

class Tile(context: Context, widthDp: Int, heightDp: Int, val accent: Int) : FrameLayout(context) {
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
        ellipsize = TextUtils.TruncateAt.END
    }

    private val fill = GradientDrawable().apply {
        cornerRadius = context.dp(14).toFloat()
        orientation = GradientDrawable.Orientation.TL_BR
    }
    private var gradient: IntArray? = null
    private val ring = GradientDrawable().apply { cornerRadius = context.dp(14).toFloat() }
    // Dark line inside the white focus ring, so the ring still shows on white app banners.
    private val ringEdge = GradientDrawable().apply { cornerRadius = context.dp(11).toFloat() }
    private val text = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(18), 0, context.dp(16), 0)
        addView(title)
        addView(detail)
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
        foreground = LayerDrawable(arrayOf(ring, ringEdge)).apply { setLayerInset(1, context.dp(3), context.dp(3), context.dp(3), context.dp(3)) }
        clipToOutline = true
        addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnFocusChangeListener { _, hasFocus ->
            val scale = if (hasFocus) 1.07f else 1f
            animate().scaleX(scale).scaleY(scale).translationZ(if (hasFocus) context.dp(8).toFloat() else 0f).setDuration(120).start()
            render()
        }
        render()
    }

    fun showStatus(label: CharSequence, dot: Int?) {
        detail.text = if (dot == null) label else SpannableStringBuilder().append("● ", ForegroundColorSpan(dot), 0).append(label)
    }

    fun showImage(image: Drawable) {
        text.visibility = GONE
        addView(ImageView(context).apply {
            setImageDrawable(image)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private var markView: ImageView? = null
    private var backdrop: ImageView? = null

    // A picture behind the text (album art), darkened on the text side; null removes it.
    fun showBackdrop(image: Drawable?) {
        if (image == null) {
            backdrop?.let(::removeView)
            backdrop = null
            return
        }
        val view = backdrop ?: ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            foreground = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xE6000000.toInt(), 0x80000000.toInt()))
        }.also {
            addView(it, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            backdrop = it
        }
        view.setImageDrawable(image)
    }

    fun showMark(mark: Drawable, sizeDp: Int) {
        markView?.let { return it.setImageDrawable(mark) }
        text.setPadding(context.dp(36 + sizeDp), 0, context.dp(16), 0)
        markView = ImageView(context).apply {
            setImageDrawable(mark)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = context.dp(8).toFloat() }
        }
        addView(markView, LayoutParams(context.dp(sizeDp), context.dp(sizeDp), Gravity.CENTER_VERTICAL or Gravity.START).apply {
            marginStart = context.dp(22)
        })
    }

    fun fillWith(colors: IntArray) {
        gradient = colors
        render()
    }

    fun showIcon(icon: Drawable, label: CharSequence) {
        title.text = label
        title.textSize = 15f
        detail.visibility = GONE
        text.gravity = Gravity.BOTTOM
        text.setPadding(context.dp(16), 0, context.dp(16), context.dp(12))
        addView(ImageView(context).apply { setImageDrawable(icon) }, LayoutParams(context.dp(36), context.dp(36), Gravity.TOP or Gravity.START).apply {
            setMargins(context.dp(16), context.dp(12), 0, 0)
        })
    }

    private fun render() {
        val colors = gradient
        if (colors != null) fill.colors = colors else fill.setColor(blend(Palette.SURFACE, accent, if (active) 0.6f else 0.08f))
        when {
            isFocused -> ring.setStroke(context.dp(3), Palette.TEXT)
            active -> ring.setStroke(context.dp(2), accent)
            else -> ring.setStroke(0, Color.TRANSPARENT)
        }
        ringEdge.setStroke(if (isFocused) context.dp(2) else 0, if (isFocused) 0x99000000.toInt() else Color.TRANSPARENT)
        detail.setTextColor(if (active || colors != null) Palette.TEXT_SOFT else Palette.MUTED)
    }
}
