package com.tony.nosotros.ui

import kotlin.math.PI
import kotlin.math.sin

internal object TogetherMotion {
    const val HEARTS = 4
    private const val PERIOD = 2800L
    private val positions = floatArrayOf(-30f,-9f,17f,33f)
    private val sizes = floatArrayOf(.65f,1f,.8f,.55f)
    data class Heart(val x: Float, val y: Float, val scale: Float, val opacity: Float)
    fun heart(index: Int, elapsed: Long, animated: Boolean = true): Heart {
        require(index in 0 until HEARTS)
        val x = positions[index]
        if (!animated) return Heart(x, -48f - (index % 2) * 16f,
            sizes[index], 1f)
        val phase = ((elapsed % PERIOD + index * PERIOD / HEARTS) % PERIOD) / PERIOD.toFloat()
        val wave = sin(phase * PI).toFloat().coerceAtLeast(0f)
        return Heart(x + (if (index % 2 == 0) -5f else 5f) * wave,
            -44f - 42f * phase, .58f + .3f * wave, wave)
    }
}
