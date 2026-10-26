package com.leo.tvlobby

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

class AuroraView(context: Context) : View(context) {
    private class Blob(val x: Float, val y: Float, val radius: Float, val drift: Float, val phase: Float, var color: Int)

    private val blobs = listOf(
        Blob(0.15f, 0.05f, 0.65f, 0.08f, 0f, DEFAULT_GLOW),
        Blob(0.85f, 0.25f, 0.55f, 0.10f, 2.1f, 0x337A3BDB),
        Blob(0.55f, 1.05f, 0.70f, 0.07f, 4.2f, 0x262EC4B6),
    )
    private val glow = blobs.first()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrim = Paint()
    private var time = 0f

    private val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 90_000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            time = it.animatedValue as Float
            invalidate()
        }
    }
    private var tint: ValueAnimator? = null

    fun glowWith(color: Int?) {
        val target = color?.let { (it and 0x00FFFFFF) or 0x59000000 } ?: DEFAULT_GLOW
        tint?.cancel()
        tint = ValueAnimator.ofArgb(glow.color, target).apply {
            duration = 700
            addUpdateListener {
                glow.color = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    fun resume() = if (clock.isStarted) clock.resume() else clock.start()

    fun pause() = clock.pause()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scrim.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Palette.BACKGROUND)
        val w = width.toFloat()
        val h = height.toFloat()
        val angle = (time * 2 * PI).toFloat()
        blobs.forEach { blob ->
            val x = (blob.x + blob.drift * sin(angle + blob.phase)) * w
            val y = (blob.y + blob.drift * sin(angle * 2 + blob.phase)) * h
            paint.shader = RadialGradient(x, y, blob.radius * w, blob.color, blob.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        canvas.drawRect(0f, 0f, w, h, scrim)
    }

    private companion object {
        const val DEFAULT_GLOW = 0x4D3B5BDB
    }
}
