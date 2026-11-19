package com.tony.nosotros.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExperienceDesignDeviceTest {
    @get:Rule val ui = createComposeRule()

    @Test fun dockFitsNarrowPhoneWithLargeFontsAndStillNavigates() {
        val selected = mutableStateOf(0)
        val width = mutableStateOf(320)
        ui.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                NosotrosTheme {
                    Box(Modifier.width(width.value.dp)) {
                        FloatingDock(selected.value, listOf(DockItem(0,"Mapa",Ic.Map),
                            DockItem(5,"Encontrar",Ic.Locate),DockItem(4,"Voz",Ic.Mic),
                            DockItem(6,"Más",Ic.More))) { selected.value = it }
                    }
                }
            }
        }
        for (w in listOf(320, 280, 480)) {
            ui.runOnIdle { width.value = w }
            val dock = ui.onNodeWithTag("floating-dock").fetchSemanticsNode().boundsInRoot
            listOf("Mapa","Encontrar","Voz","Más").forEach {
                val label = ui.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Dock label outside container at $w",label.left >= dock.left && label.right <= dock.right)
            }
            ui.onNodeWithText("Voz").performClick()
            ui.runOnIdle { assertEquals(4,selected.value) }
            ui.onNodeWithText("Más").performClick()
            ui.runOnIdle { assertEquals(6,selected.value) }
        }
    }

    @Test fun hiddenVoiceDoesNotLeaveAnEmptyNavigationSlot() {
        ui.setContent { NosotrosTheme { FloatingDock(0,listOf(
            DockItem(0,"Mapa",Ic.Map),DockItem(5,"Encontrar",Ic.Locate),DockItem(6,"Más",Ic.More))) {} } }
        ui.onNodeWithText("Voz").assertDoesNotExist()
        ui.onNodeWithText("Encontrar").assertIsDisplayed()
        ui.onNodeWithText("Más").assertIsDisplayed()
    }
}
