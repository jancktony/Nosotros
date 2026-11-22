package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Static gradients: translucent surfaces without live blur over the 3D map.
fun Modifier.experienceBackground(): Modifier = background(Brush.linearGradient(listOf(
    lerp(C.bg, C.me, if (C.dark) .065f else .025f), C.bg,
    lerp(C.bg, C.partner, if (C.dark) .055f else .025f),
)))

fun Modifier.glassPanel(radius: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(Brush.verticalGradient(listOf(
        C.surface2.copy(alpha = if (C.dark) .82f else .88f),
        C.surface.copy(alpha = if (C.dark) .78f else .90f),
    ))).border(1.dp, C.text.copy(alpha = if (C.dark) .09f else .10f), shape)
}

data class DockItem(val id: Int, val label: String, val icon: ImageVector)

@Composable
fun FloatingDock(selected: Int, items: List<DockItem>, onSelect: (Int) -> Unit) {
    Row(Modifier.widthIn(max = 480.dp).fillMaxWidth().glassPanel(30.dp).padding(7.dp).testTag("floating-dock"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEach { item ->
            val active = selected == item.id
            Column(Modifier.weight(1f).clip(RoundedCornerShape(23.dp))
                .background(if (active) (if(C.p.id=="redblack") C.me else C.me.copy(alpha = .14f)) else Color.Transparent)
                .semantics { this.selected = active }
                .clickable(role = Role.Tab) { onSelect(item.id) }
                .heightIn(min = 56.dp).padding(horizontal = 2.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(item.icon, null, tint = if (active) (if(C.p.id=="redblack") C.meInk else C.me) else C.sub, modifier = Modifier.size(22.dp))
                Text(item.label, color = if (active) (if(C.p.id=="redblack") C.meInk else C.me) else C.sub, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
