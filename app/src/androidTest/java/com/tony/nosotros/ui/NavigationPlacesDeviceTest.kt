package com.tony.nosotros.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tony.nosotros.data.Place
import com.tony.nosotros.presence.AppIcons
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationPlacesDeviceTest {
    @get:Rule val ui = createComposeRule()
    @Test fun allContoursKeepFourWorkingTabs() {
        val style=mutableStateOf("wave");val selected=mutableStateOf(0)
        ui.setContent { NosotrosTheme { Box(Modifier.width(280.dp)) {
            FloatingDock(selected.value,listOf(DockItem(0,"Mapa",Ic.Map),DockItem(5,"Encontrar",Ic.Locate),
                DockItem(4,"Voz",Ic.Mic),DockItem(6,"Más",Ic.More)),style.value,"none") { selected.value=it }
        } } }
        for (kind in listOf("wave","spotlight","notch")) {
            ui.runOnIdle { style.value=kind }
            ui.onNodeWithText("Voz").performClick()
            ui.runOnIdle { assertEquals(4,selected.value) }
            ui.onNodeWithText("Mapa").performClick()
            ui.runOnIdle { assertEquals(0,selected.value) }
        }
    }
    @Test fun typedAddressNeedsExplicitPinReviewBeforeSaving() {
        val editing=mutableStateOf(Place());var saved: Place?=null
        ui.setContent { NosotrosTheme {
            PlaceDialog(editing.value,"Nuevo lugar",onSave={saved=it},
                onMove={ editing.value=it.copy(lat=11.0,lng=-74.8) },onDismiss={})
        } }
        ui.onNodeWithText("Ciudad").performTextInput("Barranquilla")
        ui.onNodeWithText("Dirección exacta").performTextInput("Calle 56 #43-20")
        ui.onNodeWithText("Guardar").assertIsNotEnabled()
        ui.onNodeWithText("Confirmar punto en el mapa").performClick()
        ui.onNodeWithText("Guardar").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals("Calle 56 #43-20, Barranquilla",saved?.displayName);assertEquals(11.0,saved?.lat?:0.0,0.0) }
        ui.onNodeWithText("Ciudad").performTextInput(" centro")
        ui.onNodeWithText("Guardar").assertIsNotEnabled()
    }
    @Test fun installedAppIconIsSmallPngAndInvalidPayloadsAreRejected() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val icon=AppIcons.capture(ctx,ctx.packageName)
        assertTrue(icon.isNotEmpty());assertTrue(icon.length<=8192)
        val bitmap=AppIcons.decode(icon);assertNotNull(bitmap);assertEquals(32,bitmap?.width);bitmap?.recycle()
        assertNull(AppIcons.decode("https://example.invalid/icon.png"))
        assertNull(AppIcons.decode("iVBORw0KGgo"+"A".repeat(8192)))
    }
}
