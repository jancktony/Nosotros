package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemePaletteDeviceTest {
 @get:Rule val ui=createComposeRule()
 @Test fun selectingApprovedThemesRepaintsRealControls(){
  val selected=mutableStateOf("pink")
  ui.setContent{NosotrosTheme{Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())){
   Box(Modifier.size(40.dp).background(C.me).testTag("theme-color"))
   Palettes.choices.take(4).forEach{(label,p)->ThemeTile(label,p,selected.value==p.id,Modifier.fillMaxWidth()) {C.p=p;selected.value=p.id}}
  }}}
  try{
   for((label,p) in Palettes.choices.take(4)){
    ui.onNodeWithText(label).performScrollTo().performClick();ui.waitForIdle()
    ui.runOnIdle{assertEquals(p.id,C.p.id)}
    ui.onNodeWithTag("theme-color").performScrollTo()
    val bitmap=ui.onNodeWithTag("theme-color").captureToImage().asAndroidBitmap()
    assertEquals(p.me.toArgb(),bitmap.getPixel(bitmap.width/2,bitmap.height/2))
   }
  }finally{ui.runOnIdle{C.p=Palettes.Pink}}
 }
 @Test fun moreTilesNavigateUnderEveryTheme(){
  val target=mutableStateOf(0)
  ui.setContent{NosotrosTheme{Box(Modifier.width(320.dp).height(700.dp)){MoreScreen({}, {target.value=it})}}}
  try{for((_,p) in Palettes.choices.take(4)){
   ui.runOnIdle{C.p=p};ui.onNodeWithText("Galería").performScrollTo().performClick();ui.runOnIdle{assertEquals(3,target.value)}
  }}finally{ui.runOnIdle{C.p=Palettes.Pink}}
 }
}
