package com.tony.nosotros.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import com.tony.nosotros.data.Member
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class MapPanelDeviceTest {
    @get:Rule val ui = createComposeRule()
    @Before fun offlineBackend() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        if(com.google.firebase.FirebaseApp.getApps(context).isEmpty())
            com.google.firebase.FirebaseApp.initializeApp(context,com.google.firebase.FirebaseOptions.Builder()
                .setProjectId("demo-nosotros").setApplicationId("1:123456789:android:abcdef0123456789")
                .setApiKey("offline-test-key").build())
        com.google.android.gms.tasks.Tasks.await(
            com.google.firebase.firestore.FirebaseFirestore.getInstance().disableNetwork(),10,TimeUnit.SECONDS)
    }
    @Test fun collapsedPanelNeverExposesStatisticsBehindDock() {
        val collapsed=mutableStateOf(true)
        val now=System.currentTimeMillis()
        ui.setContent {
            NosotrosTheme {
                Box(Modifier.width(320.dp).height(400.dp)) {
                    PartnerSheet(Member(uid="demo",name="Kelin",updatedAt=now,locationAt=now-60_000,
                        lat=11.0,lng=-74.0,placeName="Casa",battery=62),"demo",14.0,0L,emptyList(),
                        together=true,collapsed=collapsed.value,liveRequested=0L,eta=null,leftName=null,
                        leftAt=0L,activity=emptyList(),onLive={})
                }
            }
        }
        ui.onNodeWithText("Kelin").assertIsDisplayed()
        ui.onNodeWithText("Distancia").assertDoesNotExist()
        ui.onNodeWithText("Velocidad").assertDoesNotExist()
        ui.onNode(hasText("GPS hace",substring=true)).assertIsDisplayed()
        ui.runOnIdle { collapsed.value=false }
        ui.onNodeWithText("Estado").assertIsDisplayed()
        ui.onAllNodesWithText("Juntos").onFirst().assertIsDisplayed()
        ui.onNodeWithText("14 m").assertDoesNotExist()
    }
}
