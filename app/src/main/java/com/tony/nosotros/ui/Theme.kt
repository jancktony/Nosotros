package com.tony.nosotros.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.nosotros.data.Mode

/** Paleta de colores de un tema. "me" = color de quien usa el teléfono, "partner" = la pareja. */
data class Palette(
    val id: String, val dark: Boolean,
    val bg: Color, val surface: Color, val surface2: Color, val line: Color,
    val text: Color, val sub: Color,
    val me: Color, val meInk: Color, val partner: Color, val partnerInk: Color,
    val ok: Color, val warn: Color, val danger: Color, val glass: Color,
    val mapStyle: String,
)

object Palettes {
    val Night = Palette(
        id = "night", dark = true,
        bg = Color(0xFF0E171E), surface = Color(0xFF18232C), surface2 = Color(0xFF202F39),
        line = Color(0xFF2C3A43), text = Color(0xFFF2F3EF), sub = Color(0xFF97A9B1),
        me = Color(0xFFA1E4CF), meInk = Color(0xFF04211D),
        partner = Color(0xFFEFC4D3), partnerInk = Color(0xFF3B0A24),
        ok = Color(0xFF86EFAC), warn = Color(0xFFFCD34D), danger = Color(0xFFFCA5A5),
        glass = Color(0xF0141B24),
        mapStyle = "https://tiles.openfreemap.org/styles/dark",
    )
    val Pink = Palette(
        id = "pink", dark = false,
        bg = Color(0xFFF9F0F3), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFFCE4EF),
        line = Color(0x33BE185D), text = Color(0xFF3B0A24), sub = Color(0xFF7A2E55),
        me = Color(0xFFA95476), meInk = Color(0xFFFFFFFF),
        partner = Color(0xFF0F766E), partnerInk = Color(0xFFFFFFFF),
        ok = Color(0xFF15803D), warn = Color(0xFFB45309), danger = Color(0xFFB91C1C),
        glass = Color(0xF5FFFFFF),
        mapStyle = "https://tiles.openfreemap.org/styles/liberty",
    )
    val Light = Pink.copy(id = "light", bg = Color(0xFFF5F4F0), surface = Color.White, surface2 = Color(0xFFEBECE6),
        line = Color(0xFFE4E7DE), text = Color(0xFF223B33), sub = Color(0xFF728177), me = Color(0xFF376F5A),
        meInk = Color.White, partner = Color(0xFFAB6880), partnerInk = Color.White, glass = Color(0xF5FFFFFF))
    fun of(id: String?) = when (id) { "pink" -> Pink; "light" -> Light; else -> Night }
}

/** Colores del tema activo. Cambiar [p] repinta toda la app. */
object C {
    var p by mutableStateOf(Palettes.Night)
    val dark get() = p.dark
    val bg get() = p.bg
    val surface get() = p.surface
    val surface2 get() = p.surface2
    val line get() = p.line
    val text get() = p.text
    val sub get() = p.sub
    val me get() = p.me
    val meInk get() = p.meInk
    val partner get() = p.partner
    val partnerInk get() = p.partnerInk
    val ok get() = p.ok
    val warn get() = p.warn
    val danger get() = p.danger
    val glass get() = p.glass
}

@Composable
fun NosotrosTheme(content: @Composable () -> Unit) {
    val base = if (C.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = C.me, onPrimary = C.meInk,
        secondary = C.partner, onSecondary = C.partnerInk,
        background = C.bg, onBackground = C.text,
        surface = C.surface, onSurface = C.text,
        surfaceVariant = C.surface2, onSurfaceVariant = C.sub,
        surfaceContainer = C.surface, surfaceContainerHigh = C.surface2,
        surfaceContainerLow = C.surface, surfaceContainerHighest = C.surface2,
        surfaceContainerLowest = C.surface,
        outline = C.sub, outlineVariant = C.line, error = C.danger,
    )
    val t = Typography()
    val typo = t.copy(
        headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp),
        bodyMedium = TextStyle(fontSize = 15.sp),
        bodySmall = TextStyle(fontSize = 14.sp),
        labelSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
    )
    MaterialTheme(colorScheme = scheme, typography = typo) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides C.text, content = content,
        )
    }
}

/** Íconos de trazo fino (estilo consistente con el diseño). */
object Ic {
    fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

    val P_CAR = listOf("M5 17H3v-5l2-5h14l2 5v5h-2", "M9.5 17h5", circle(7.5f, 17f, 2f), circle(16.5f, 17f, 2f), "M3 12h18")
    val P_MOTO = listOf(circle(5f, 17f, 3f), circle(19f, 17f, 3f), "M5 17l4-6h5l3 6", "M11 11 9 7H6", "M14 7h3l2 4")
    val P_BUS = listOf("M6 3h12a2 2 0 0 1 2 2v12H4V5a2 2 0 0 1 2-2z", "M4 11h16", "M7 17v3M17 17v3", "M8 14h.01M16 14h.01")
    val P_WALK = listOf(circle(13f, 4f, 2f), "M9 21l2-6 3 3v3", "M7 12l3-4 4 1 2 4", "M11 15l1.5-6")
    val P_BIKE = listOf(circle(6f, 17f, 3.5f), circle(18f, 17f, 3.5f), "M6 17l4-8h5l3 8", "M10 9 8 6H6", "M12 17l3-8")
    val P_PIN = listOf("M12 21s7-6.5 7-12a7 7 0 0 0-14 0c0 5.5 7 12 7 12z", circle(12f, 9f, 2.5f))
    val P_PAUSE = listOf("M9 5v14M15 5v14")

    private val cache = HashMap<String, ImageVector>()
    fun icon(name: String, paths: List<String>, width: Float = 2f): ImageVector = cache.getOrPut(name) {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            b.addPath(
                pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.White),
                strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        b.build()
    }

    val Car get() = icon("car", P_CAR)
    val Moto get() = icon("moto", P_MOTO)
    val Bus get() = icon("bus", P_BUS)
    val Walk get() = icon("walk", P_WALK)
    val Bike get() = icon("bike", P_BIKE)
    val Pin get() = icon("pin", P_PIN)
    val Pause get() = icon("pause", P_PAUSE)
    val Home get() = icon("home", listOf("M3 10.5 12 3l9 7.5V21H3z", "M9 21v-6h6v6"))
    val Clock get() = icon("clock", listOf(circle(12f, 12f, 9f), "M12 7v5l3 2"))
    val Map get() = icon("map", listOf("M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2z", "M9 4v14M15 6v14"))
    val Gear get() = icon("gear", listOf(circle(12f, 12f, 3f), "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"))
    val Live get() = icon("live", listOf(circle(12f, 12f, 2f), "M16.2 7.8a6 6 0 0 1 0 8.5M7.8 16.2a6 6 0 0 1 0-8.5M19.1 4.9a10 10 0 0 1 0 14.1M4.9 19.1a10 10 0 0 1 0-14.1"))
    val Nav get() = icon("nav", listOf("M3 11l19-9-9 19-2-8-8-2z"))
    val Plus get() = icon("plus", listOf("M12 5v14M5 12h14"))
    val Trash get() = icon("trash", listOf("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"))
    val Locate get() = icon("locate", listOf(circle(12f, 12f, 4f), "M12 2v3M12 19v3M2 12h3M19 12h3"))
    val Left get() = icon("left", listOf("M15 6l-6 6 6 6"))
    val Right get() = icon("right", listOf("M9 6l6 6-6 6"))
    val Check get() = icon("check", listOf("M5 12l5 5 9-10"))
    val Close get() = icon("close", listOf("M6 6l12 12M18 6 6 18"))
    val Battery get() = icon("battery", listOf("M4 7h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M22 11v2"))
    val Bolt get() = icon("bolt", listOf("M13 2 4 14h7l-1 8 9-12h-7z"))
    val Heart get() = icon("heart", listOf("M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"))
    val Layers get() = icon("layers", listOf("M12 3 2 8l10 5 10-5-10-5z", "m2 16 10 5 10-5", "m2 12 10 5 10-5"))
    val Calendar get() = icon("cal", listOf("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3 10h18M8 3v4M16 3v4"))
    val Palette get() = icon("palette", listOf(circle(12f, 12f, 9f), circle(8f, 10f, 1f), circle(12f, 7.5f, 1f), circle(16f, 10f, 1f), "M12 21a3 3 0 0 1 0-6h2"))
    val Bell get() = icon("bell", listOf("M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0"))
    val User get() = icon("user", listOf(circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0"))
    val Widget get() = icon("widget", listOf("M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"))
    val Info get() = icon("info", listOf(circle(12f, 12f, 9f), "M12 11v6M12 7.5v.01"))
    val Link get() = icon("link", listOf("M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"))
    val Mic get() = icon("microphone", listOf("M8 5a4 4 0 0 1 8 0v7a4 4 0 0 1-8 0V5z", "M4 11a8 8 0 0 0 16 0M12 19v4M8 23h8"))
    val Sparkle get() = icon("sparkle", listOf("M12 3l2 5 5 2-5 2-2 5-2-5-5-2 5-2z"))
}

fun Mode.icon(): ImageVector = when (this) {
    Mode.CAR, Mode.VEHICLE -> Ic.Car
    Mode.MOTO -> Ic.Moto
    Mode.BUS -> Ic.Bus
    Mode.WALK -> Ic.Walk
    Mode.BIKE -> Ic.Bike
    else -> Ic.Pin
}

fun Mode.paths(): List<String> = when (this) {
    Mode.CAR, Mode.VEHICLE -> Ic.P_CAR
    Mode.MOTO -> Ic.P_MOTO
    Mode.BUS -> Ic.P_BUS
    Mode.WALK -> Ic.P_WALK
    Mode.BIKE -> Ic.P_BIKE
    else -> Ic.P_PIN
}
