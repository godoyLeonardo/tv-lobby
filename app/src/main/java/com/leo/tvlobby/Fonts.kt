package com.leo.tvlobby

import android.content.Context
import android.graphics.Typeface

object Fonts {
    private val cache = mutableMapOf<Int, Typeface>()

    fun manrope(context: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        Typeface.Builder(context.assets, "fonts/Manrope.ttf").setFontVariationSettings("'wght' $weight").build()
    }
}
