package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.nosotros.data.Mode
import com.tony.nosotros.data.Trip
import com.tony.nosotros.data.Visit
import com.tony.nosotros.util.TimeFmt

@Composable
fun Avatar(initial: String, color: Color, ink: Color, size: Dp = 44.dp, dim: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (dim) Color(0xFF4B5563) else color),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = if (dim) Color(0xFFE5E7EB) else ink, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color = C.text, bg: Color = C.surface2) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, color: Color = C.me, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.16f) else C.surface2)
            .border(1.dp, if (selected) color.copy(alpha = 0.6f) else C.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) color else C.sub, modifier = Modifier.size(16.dp))
        Text(text, color = if (selected) color else C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = C.sub, modifier = modifier)
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(C.surface)
            .border(1.dp, C.line, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) { content() }
}

/** Una entrada de la línea de tiempo: visita a un lugar o trayecto. */
sealed class TimelineItem(val time: Long) {
    class V(val v: Visit) : TimelineItem(v.start)
    class T(val t: Trip) : TimelineItem(t.start)
}

fun buildTimeline(visits: List<Visit>, trips: List<Trip>, uid: String?): List<TimelineItem> =
    (visits.filter { it.uid == uid }.map { TimelineItem.V(it) } + trips.filter { it.uid == uid }.map { TimelineItem.T(it) })
        .sortedByDescending { it.time }

@Composable
fun TimelineRow(item: TimelineItem, accent: Color, onClick: (() -> Unit)? = null) {
    val (icon, title, subtitle, time) = when (item) {
        is TimelineItem.V -> {
            val v = item.v
            val dur = (v.end ?: System.currentTimeMillis()) - v.start
            Quad(Ic.Pin, v.placeName, if (v.end == null) "Aquí ahora · ${TimeFmt.duration(dur)}" else TimeFmt.duration(dur),
                if (v.end == null) TimeFmt.hour(v.start) else "${TimeFmt.hour(v.start)}–${TimeFmt.hour(v.end)}")
        }
        is TimelineItem.T -> {
            val t = item.t
            val route = listOfNotNull(t.from, t.to).joinToString(" → ").ifEmpty { "Trayecto" }
            Quad(t.mode.icon(), "${t.mode.label} · $route",
                "${TimeFmt.km(t.distanceM)} · ${TimeFmt.duration(t.end - t.start)}",
                "${TimeFmt.hour(t.start)}–${TimeFmt.hour(t.end)}")
        }
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint = if (item is TimelineItem.T) accent else C.text)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = C.sub, fontSize = 13.sp)
        }
        Text(time, color = C.sub, fontSize = 13.sp)
    }
}

private data class Quad(val a: ImageVector, val b: String, val c: String, val d: String)
