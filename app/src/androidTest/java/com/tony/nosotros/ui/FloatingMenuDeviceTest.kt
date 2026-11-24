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
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FloatingMenuDeviceTest {
    @get:Rule val ui = createComposeRule()

    @Test fun openingKeepsCurrentScreenAndSelectionNavigatesThenCloses() {
        val selected = mutableStateOf(0)
        ui.setContent { NosotrosTheme {
            FloatingNavigation(selected.value, true, "wave", "none", {}, { selected.value = it })
        } }
        ui.onNodeWithTag("dock-item-6").performClick()
        ui.onNodeWithTag("floating-more-menu").assertIsDisplayed()
        ui.onNodeWithText("Cerrar").assertIsDisplayed()
        ui.runOnIdle { assertEquals(0, selected.value) }
        ui.onNodeWithTag("more-option-1").performClick()
        ui.onNodeWithTag("floating-more-menu").assertDoesNotExist()
        ui.runOnIdle { assertEquals(1, selected.value) }
        ui.onNodeWithTag("dock-item-6").assertIsSelected()
        ui.onNodeWithTag("dock-item-6").performClick()
        Espresso.pressBack()
        ui.onNodeWithTag("floating-more-menu").assertDoesNotExist()
        ui.runOnIdle { assertEquals(1, selected.value) }
    }

    @Test fun narrowLargeTextWithoutVoiceStillOffersAllOptionsAndProfile() {
        var profiles = 0
        ui.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                NosotrosTheme { Box(Modifier.width(280.dp)) {
                    FloatingNavigation(0, false, "notch", "none", { profiles++ }, {})
                } }
            }
        }
        ui.onNodeWithText("Voz").assertDoesNotExist()
        ui.onNodeWithTag("dock-item-6").performClick()
        listOf("Historial", "Lugares", "Fotos", "Perfil").forEach { ui.onNodeWithText(it).assertIsDisplayed() }
        ui.onNodeWithTag("more-option-7").performClick()
        ui.onNodeWithTag("floating-more-menu").assertDoesNotExist()
        ui.runOnIdle { assertEquals(1, profiles) }
    }
}
