package com.tony.nosotros.ui

import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK").close()
        ui.waitForIdle()
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

    @Test fun menuNeverTargetsImeOrRestoresEditorFocusWhenNavigating() {
        val selected = mutableStateOf(0)
        val editorFocused = mutableStateOf(false)
        var popupWasGoneAtNavigation = false
        ui.setContent { NosotrosTheme {
            Box(Modifier.fillMaxSize()) {
                if (selected.value == 0) BasicTextField("", {}, modifier = Modifier
                    .testTag("previous-editor").onFocusChanged { editorFocused.value = it.isFocused })
                else BasicTextField("", {}, modifier = Modifier.testTag("destination-editor"))
                Box(Modifier.align(Alignment.BottomCenter)) {
                    FloatingNavigation(selected.value, true, "wave", "slide", {}, {
                        popupWasGoneAtNavigation = WindowInspector.getGlobalWindowViews()
                            .none { view -> view.javaClass.simpleName == "PopupLayout" }
                        selected.value = it
                    })
                }
            }
        } }
        ui.onNodeWithTag("previous-editor").performClick().assertIsFocused()
        ui.onNodeWithTag("dock-item-6").performClick()
        ui.runOnIdle {
            assertFalse("Opening navigation must clear the old text editor", editorFocused.value)
            val popup = WindowInspector.getGlobalWindowViews()
                .single { it.javaClass.simpleName == "PopupLayout" }
            val flags = (popup.layoutParams as WindowManager.LayoutParams).flags
            assertTrue("Non-editable menu must be excluded from IME targeting",
                flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0)
            assertEquals("Keep key events and Back accessible", 0,
                flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        ui.onNodeWithTag("more-option-2").performClick()
        ui.onNodeWithTag("floating-more-menu").assertDoesNotExist()
        ui.onNodeWithTag("destination-editor").assertIsNotFocused()
        ui.runOnIdle { assertTrue("Destination must open after the popup is removed", popupWasGoneAtNavigation) }
    }

}
