package com.tony.nosotros.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.tony.nosotros.ui.holdToTalk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HoldGestureTest {
    @get:Rule val ui=createComposeRule()
    @Test fun movingOutsideButtonDoesNotCancelUntilFingerIsLifted() {
        var held=false;var released=0;var canceled=0
        ui.setContent { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.size(130.dp).background(Color.Green).testTag("hold").holdToTalk(true,{held=true;true},{held=false;released++},{held=false;canceled++}))
            Spacer(Modifier.height(1800.dp))
        } }
        ui.onNodeWithTag("hold").performTouchInput { down(center);moveBy(Offset(40f,180f));advanceEventTime(300) }
        ui.runOnIdle { assertTrue(held);assertEquals(0,canceled);assertEquals(0,released) }
        ui.onNodeWithTag("hold").performTouchInput { up() }
        ui.runOnIdle { assertFalse(held);assertEquals(1,released);assertEquals(0,canceled) }
    }
    @Test fun cancellationStopsInsteadOfLeavingTheMicHeld() {
        var held=false;var canceled=0
        ui.setContent { Box(Modifier.size(130.dp).testTag("hold").holdToTalk(true,{held=true;true},{held=false},{held=false;canceled++})) }
        ui.onNodeWithTag("hold").performTouchInput { down(center);cancel() }
        ui.runOnIdle { assertFalse(held);assertEquals(1,canceled) }
    }
}
