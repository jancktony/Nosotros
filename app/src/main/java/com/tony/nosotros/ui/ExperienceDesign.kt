package com.tony.nosotros.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
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
fun FloatingDock(selected: Int, items: List<DockItem>, style: String = "wave", motion: String = "slide", moreExpanded: Boolean? = null, onSelect: (Int) -> Unit) {
    if (items.isEmpty()) return
    val activeId = selected.takeIf { id -> items.any { it.id == id } } ?: items.last().id
    val index = items.indexOfFirst { it.id == activeId }
    val enabled = android.animation.ValueAnimator.areAnimatorsEnabled() && motion != "none"
    val position by animateFloatAsState(index.toFloat(), animationSpec =
        if (!enabled || motion == "fade") snap() else tween(if (motion == "spring") 480 else 320,
            easing = if (motion == "spring") CubicBezierEasing(.34f, 1.45f, .64f, 1f) else CubicBezierEasing(.22f, 1f, .36f, 1f)), label = "dock-position")
    val fade = remember { Animatable(1f) }
    LaunchedEffect(index, motion) {
        if (enabled && motion == "fade") { fade.snapTo(0f); fade.animateTo(1f, tween(200)) }
        else fade.snapTo(1f)
    }
    val density = LocalDensity.current
    val dockHeight = with(density) { maxOf(54.dp, (36.dp.toPx() + 11.sp.toPx() * 1.5f).toDp()) }
    val color = C.me
    val surface = C.surface.copy(alpha = if (C.dark) .8f else .85f)
    val line = C.text.copy(alpha = .18f)
    Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().height(dockHeight).testTag("floating-dock")) {
        Canvas(Modifier.matchParentSize()) {
            val u = 1.dp.toPx()
            val w = size.width; val h = size.height; val pad = 5 * u
            val cell = (w - 2 * pad) / items.size
            val x = pad + cell * (position.coerceIn(0f, (items.size - 1).toFloat()) + .5f)
            val alpha = fade.value
            if (style == "notch") {
                val l = (x - 34 * u).coerceAtLeast(21 * u)
                val r = (x + 34 * u).coerceAtMost(w - 21 * u)
                val path = Path().apply {
                    moveTo(24 * u, u); lineTo(l, u)
                    cubicTo((l+x)/2, u, (l+x)/2, 26*u, x, 26*u)
                    cubicTo((r+x)/2, 26*u, (r+x)/2, u, r, u)
                    lineTo(w-24*u,u); quadraticBezierTo(w-u,u,w-u,25*u)
                    lineTo(w-u,h-25*u);quadraticBezierTo(w-u,h-u,w-24*u,h-u)
                    lineTo(24*u,h-u);quadraticBezierTo(u,h-u,u,h-25*u)
                    lineTo(u,25*u);quadraticBezierTo(u,u,24*u,u);close()
                }
                drawPath(path, surface); drawPath(path, line, style = Stroke(u))
                drawCircle(color.copy(alpha=.1f*alpha), 19*u, Offset(x,6*u))
                drawCircle(lerp(C.surface, color, .32f).copy(alpha=alpha),17*u,Offset(x,5*u))
                drawCircle(color.copy(alpha=.8f*alpha),17*u,Offset(x,5*u),style=Stroke(u))
            } else {
                drawRoundRect(surface, cornerRadius = CornerRadius(26*u))
                drawRoundRect(line, cornerRadius = CornerRadius(26*u), style = Stroke(u))
                if (style == "spotlight") {
                    val path = Path().apply {
                        moveTo(x-cell*.16f,u);lineTo(x+cell*.16f,u)
                        lineTo(x+cell*.5f,h-8*u);lineTo(x-cell*.5f,h-8*u);close()
                    }
                    drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha=.55f*alpha),color.copy(alpha=0f))))
                    drawRoundRect(color.copy(alpha=alpha),Offset(x-cell*.16f,u),Size(cell*.32f,3*u),CornerRadius(2*u))
                } else {
                    val l = (x-34*u).coerceAtLeast(u);val r = (x+34*u).coerceAtMost(w-u)
                    val path = Path().apply {
                        moveTo(u,h-2*u);lineTo(l,h-2*u)
                        cubicTo((l+x)/2,h-2*u,(l+x)/2,5*u,x,5*u)
                        cubicTo((r+x)/2,5*u,(r+x)/2,h-2*u,r,h-2*u)
                        lineTo(w-u,h-2*u)
                    }
                    drawPath(path,color.copy(alpha=.14f*alpha),style=Stroke(7*u))
                    drawPath(path,color.copy(alpha=alpha),style=Stroke(1.7f*u))
                }
            }
        }
        Row(Modifier.fillMaxSize().padding(5.dp), verticalAlignment=Alignment.CenterVertically) {
            items.forEach { item ->
                val active = activeId == item.id
                Column(Modifier.weight(1f).heightIn(min=44.dp)
                    .testTag("dock-item-${item.id}").semantics { this.selected = active; if (item.id == 6 && moreExpanded != null) stateDescription = if (moreExpanded) "Menú abierto" else "Menú cerrado" }.clickable(role=Role.Tab) { onSelect(item.id) }.padding(horizontal=1.dp,vertical=3.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Icon(item.icon,null,tint=if(active && style=="notch") C.text else if(active) C.me else C.sub,
                        modifier=Modifier.size(18.dp).offset(y=if(active && style=="notch")(-13).dp else 0.dp))
                    Text(item.label,color=if(active) C.me else C.sub,fontSize=11.sp,fontWeight=FontWeight.SemiBold,
                        maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}
