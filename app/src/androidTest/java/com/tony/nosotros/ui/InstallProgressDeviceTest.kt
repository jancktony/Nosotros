package com.tony.nosotros.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tony.nosotros.update.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InstallProgressDeviceTest {
    @get:Rule val ui = createComposeRule()

    @Test fun downloadCanMinimizeAndExpandWithoutCallingItInstalled() {
        ui.setContent {
            var expanded by remember { mutableStateOf(true) }
            NosotrosTheme {
                UpdateProgressPanel(InstallSnapshot("1.15.5", InstallStage.DOWNLOADING, 50, 100), expanded,
                    onExpand = { expanded = true }, onMinimize = { expanded = false },
                    onCancel = {}, onRetry = {}, onConfirm = {}, onDismiss = {})
            }
        }
        ui.onNodeWithText("50 %").assertIsDisplayed()
        ui.onNodeWithText("Actualización instalada").assertDoesNotExist()
        ui.onNodeWithText("Minimizar").performClick()
        ui.onNodeWithText("Descargando actualización").performClick()
        ui.onNodeWithText("50 %").assertIsDisplayed()
    }

    @Test fun confirmationRemainsReachableWithLargeText() {
        var confirmations = 0
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                NosotrosTheme {
                    UpdateProgressPanel(InstallSnapshot("1.15.5", InstallStage.CONFIRMATION, 100, 100), true,
                        {}, {}, {}, {}, { confirmations++ }, {})
                }
            }
        }
        ui.onNodeWithText("Continuar instalación").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(1, confirmations) }
        ui.onNodeWithText("100 %").assertDoesNotExist()
        ui.onNodeWithText("Cancelar descarga").assertDoesNotExist()
        ui.onNodeWithText("Actualización instalada").assertDoesNotExist()
    }

    @Test fun failedDownloadOffersRetryAndConfirmedInstallationShowsDone() {
        var state by mutableStateOf(InstallSnapshot("1.15.5", InstallStage.FAILED, message = "Se perdió la conexión."))
        var retries = 0
        ui.setContent {
            NosotrosTheme { UpdateProgressPanel(state, true, {}, {}, {}, { retries++ }, {}, {}) }
        }
        ui.onNodeWithText("Volver a intentar").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(1, retries); state = state.copy(stage = InstallStage.INSTALLED) }
        ui.onNodeWithText("Actualización instalada").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Volver a intentar").assertDoesNotExist()
    }
}
