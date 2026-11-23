package com.tony.nosotros.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun IosBattery(level: Int, charging: Boolean = false, modifier: Modifier = Modifier) {
    val pct = level.coerceIn(0, 100)
    val tint = if (pct < 20 && !charging) C.danger else C.ok
    val ink = C.text
    val fillInk = if (tint.luminance() > .45f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
    Canvas(modifier.size(40.dp, 22.dp).semantics { contentDescription = "Batería $pct por ciento" + if (charging) ", cargando" else "" }) {
        val unit = size.width / 40f
        drawRoundRect(tint.copy(alpha = .65f), Offset(unit, 3 * unit), Size(32 * unit, 16 * unit), CornerRadius(4 * unit), style = Stroke(1.3f * unit))
        if (charging) {
            val bolt = Path().apply {
                moveTo(38*unit,3*unit);lineTo(34*unit,11*unit);lineTo(37*unit,11*unit)
                lineTo(35*unit,19*unit);lineTo(40*unit,9*unit);lineTo(37*unit,9*unit);close()
            }
            drawPath(bolt,tint)
        } else drawRoundRect(tint, Offset(35 * unit, 8 * unit), Size(2 * unit, 6 * unit), CornerRadius(unit))
        if (pct > 0) drawRoundRect(tint, Offset(2 * unit, 4 * unit), Size(30 * unit * pct / 100f, 14 * unit), CornerRadius(3 * unit))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.toArgb(); textSize = 11 * unit; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val baseline = 11 * unit - (paint.ascent() + paint.descent()) / 2
        drawContext.canvas.nativeCanvas.drawText(pct.toString(), 17 * unit, baseline, paint)
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.clipRect(2 * unit, 4 * unit, (2 + 30 * pct / 100f) * unit, 18 * unit)
        paint.color = fillInk.toArgb()
        canvas.drawText(pct.toString(), 17 * unit, baseline, paint)
        canvas.restore()
    }
}
