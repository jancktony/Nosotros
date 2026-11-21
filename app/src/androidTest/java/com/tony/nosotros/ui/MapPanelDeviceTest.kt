package com.tony.nosotros.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import com.tony.nosotros.data.Member
import org.junit.Assert.assertTrue
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
                    PartnerSheet(Member(uid="demo",name="Kelin",acc=12f,updatedAt=now,locationAt=now-60_000,
                        lat=11.0,lng=-74.0,placeName="Casa",battery=62),"demo",14.0,0L,emptyList(),
                        together=true,collapsed=collapsed.value,liveRequested=0L,eta=null,leftName=null,
                        leftAt=0L,appActivity=com.tony.nosotros.presence.AppActivity("Instagram","Última detección · hace 30 s","IG",true),onLive={})
                }
            }
        }
        ui.onNodeWithText("Kelin").assertIsDisplayed()
        ui.onNodeWithText("Instagram").assertIsDisplayed()
        ui.onNodeWithText("Última detección · hace 30 s").assertIsDisplayed()
        ui.onNodeWithText("Distancia").assertDoesNotExist()
        ui.onNodeWithText("Velocidad").assertDoesNotExist()
        ui.onNode(hasText("GPS hace",substring=true)).assertIsDisplayed()
        ui.runOnIdle { collapsed.value=false }
        ui.onNodeWithText("Estado").assertIsDisplayed()
        ui.onAllNodesWithText("Juntos").onFirst().assertIsDisplayed()
        ui.onNodeWithText("14 m").assertDoesNotExist()
    }

    @Test fun activityHeaderAdaptsToLargeFontsAndHidesRevokedApp() {
        val scale=mutableStateOf(1f)
        val activity=mutableStateOf(com.tony.nosotros.presence.AppActivity("Instagram","Última detección · hace 30 s","IG",true))
        var measured=0
        ui.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current.density
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density,scale.value)) {
                NosotrosTheme {
                    Box(Modifier.width(320.dp).height(500.dp)) {
                        PartnerSheet(Member(uid="demo",name="Kelin",acc=12f,lat=11.0,lng=-74.0,updatedAt=System.currentTimeMillis(),
                            locationAt=System.currentTimeMillis(),placeName="Casa"),"demo",14.0,0L,emptyList(),
                            together=true,collapsed=true,liveRequested=0L,eta=null,leftName=null,leftAt=0L,
                            appActivity=activity.value,onHeaderHeight={measured=it},onLive={})
                    }
                }
            }
        }
        ui.onNodeWithText("Instagram").assertIsDisplayed().performClick()
        ui.onNodeWithText("Actividad de Kelin").assertIsDisplayed()
        ui.onNodeWithText("Cerrar").performClick()
        val normal=ui.runOnIdle{measured}
        ui.runOnIdle { scale.value=1.6f }
        ui.runOnIdle { assertTrue("Header must adapt to larger text",measured>normal) }
        ui.onNodeWithText("Instagram").assertIsDisplayed()
        ui.runOnIdle {
            activity.value=com.tony.nosotros.presence.AppActivity.from(
                com.tony.nosotros.presence.Presence(false,"Instagram",updatedAt=System.currentTimeMillis()),
                System.currentTimeMillis())
        }
        ui.onNodeWithText("Actividad no compartida").assertIsDisplayed()
        ui.onNodeWithText("Instagram").assertDoesNotExist()
        ui.onNodeWithText("Velocidad").assertDoesNotExist()
    }
}
