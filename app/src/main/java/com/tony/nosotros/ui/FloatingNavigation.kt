package com.tony.nosotros.ui

import android.animation.ValueAnimator
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Secondary navigation floats above its anchor; opening it leaves the current screen intact. */
@Composable
fun FloatingNavigation(selected: Int, voiceAvailable: Boolean, style: String, motion: String,
    onProfile: () -> Unit, onSelect: (Int) -> Unit) {
    val expanded = remember { MutableTransitionState(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var pendingDestination by remember { mutableStateOf<Int?>(null) }
    fun dismissInput() { focus.clearFocus(force = true); keyboard?.hide() }
    // Remove the popup before composing a destination containing editable fields.
    LaunchedEffect(expanded.currentState, expanded.targetState, pendingDestination) {
        if (!expanded.currentState && !expanded.targetState) pendingDestination?.let { id ->
            pendingDestination = null
            if (id == 7) onProfile() else onSelect(id)
        }
    }
    LaunchedEffect(selected, voiceAvailable) { expanded.targetState = false }
    val animate = ValueAnimator.areAnimatorsEnabled() && motion != "none"
    val secondary = listOf(DockItem(1, "Historial", Ic.Clock), DockItem(2, "Lugares", Ic.Pin),
        DockItem(3, "Fotos", Ic.Layers), DockItem(7, "Perfil", Ic.User))
    val activeLabel = secondary.firstOrNull { it.id == selected }?.label ?: "Más"
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    val provider = remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val right = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
                return IntOffset(right.coerceIn(gap, maxOf(gap, windowSize.width - popupContentSize.width - gap)),
                    (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(gap))
            }
        }
    }
    Box(Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
        FloatingDock(if (expanded.targetState) 6 else selected, buildList {
            add(DockItem(0, "Mapa", Ic.Map)); add(DockItem(5, "Encontrar", Ic.Locate))
            if (voiceAvailable) add(DockItem(4, "Voz", Ic.Mic))
            add(DockItem(6, if (expanded.targetState) "Cerrar" else activeLabel,
                if (expanded.targetState) Ic.Close else Ic.Menu))
        }, style, motion, moreExpanded = expanded.targetState) { id ->
            dismissInput()
            if (id == 6) expanded.targetState = !expanded.targetState
            else { expanded.targetState = false; onSelect(id) }
        }
        if (expanded.currentState || expanded.targetState) {
            Popup(popupPositionProvider = provider, onDismissRequest = { expanded.targetState = false },
                // Keep keyboard/Back accessibility, but never make this non-editable window an IME target.
                properties = PopupProperties(flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                    dismissOnBackPress = true, dismissOnClickOutside = true)) {
                AnimatedVisibility(visibleState = expanded,
                    enter = fadeIn(if (animate) tween(180) else snap()) +
                        scaleIn(if (animate) tween(300) else snap(), initialScale = .15f, transformOrigin = TransformOrigin(.88f, 1f)) +
                        slideInVertically(if (animate) tween(300) else snap()) { it / 5 },
                    exit = fadeOut(if (animate) tween(160) else snap()) +
                        scaleOut(if (animate) tween(200) else snap(), targetScale = .15f, transformOrigin = TransformOrigin(.88f, 1f)) +
                        slideOutVertically(if (animate) tween(200) else snap()) { it / 5 }) {
                    Column(Modifier.widthIn(max = 238.dp)
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp * .6f).dp)
                        .glassPanel(25.dp).testTag("floating-more-menu")
                        .verticalScroll(rememberScrollState()).padding(9.dp)) {
                        secondary.forEach { item ->
                            val active = selected == item.id
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (active) C.me.copy(alpha = .16f) else androidx.compose.ui.graphics.Color.Transparent)
                                .testTag("more-option-${item.id}").semantics { this.selected = active }
                                .clickable(role = Role.Button) {
                                    dismissInput()
                                    pendingDestination = item.id
                                    expanded.targetState = false
                                }.heightIn(min = 48.dp).padding(horizontal = 13.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                                Icon(item.icon, null, tint = if (active) C.me else C.sub, modifier = Modifier.size(21.dp))
                                Text(item.label, color = if (active) C.me else C.text, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
