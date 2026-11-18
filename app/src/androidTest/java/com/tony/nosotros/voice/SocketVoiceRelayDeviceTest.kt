package com.tony.nosotros.voice

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/** Real Android sockets + authenticated Firestore + encrypted live delivery to a local Worker fixture. */
class SocketVoiceRelayDeviceTest {
    @Test fun twoPrivateClientsReconnectAndExchangeEncryptedVoiceWithoutWebRTC() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        fun backend(name:String,email:String):Pair<FirebaseApp,FirebaseFirestore> {
            val app=FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setProjectId("demo-nosotros")
                .setApplicationId("1:123456789:android:"+name).setApiKey("emulator-only-key").build(),name)
            val auth=FirebaseAuth.getInstance(app);auth.useEmulator("10.0.2.2",9099)
            Tasks.await(auth.signInWithEmailAndPassword(email,"demo-emulator-only-123"),15,TimeUnit.SECONDS)
            val db=FirebaseFirestore.getInstance(app)
            db.firestoreSettings=FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            db.useEmulator("10.0.2.2",8080)
            return app to db
        }
        val (appA,dbA)=backend("socket-alice","jancktoni@gmail.com")
        val (appB,dbB)=backend("socket-bob","kelinherrera130323@gmail.com")
        var a:SocketVoiceRelay?=null;var b:SocketVoiceRelay?=null
        val actual=ConcurrentLinkedQueue<ByteArray>()
        val packetA=VoiceWire.packet(0,listOf(byteArrayOf(1,2,3,4)),123)
        val packetB=VoiceWire.packet(0,listOf(byteArrayOf(5,6,7,8)),456)
        try {
            withContext(Dispatchers.Main) {
                a=SocketVoiceRelay("socket-integration","alice",VoiceClock{android.os.SystemClock.elapsedRealtime()},database=dbA,
                    authorized={FirebaseAuth.getInstance(appA).currentUser?.uid=="alice"},
                    received={actual.add(it)},focus={true}) { _,_-> }
                b=SocketVoiceRelay("socket-integration","bob",VoiceClock{android.os.SystemClock.elapsedRealtime()},database=dbB,
                    authorized={FirebaseAuth.getInstance(appB).currentUser?.uid=="bob"},
                    received={actual.add(it)},focus={true}) { _,_-> }
                a!!.start();b!!.start()
            }
            withTimeout(25_000){while(a?.ready!=true||b?.ready!=true)delay(50)}
            withContext(Dispatchers.Main){assertTrue(a!!.enqueue(packetA));assertTrue(b!!.enqueue(packetB))}
            withTimeout(10_000){while(actual.size<2)delay(50)}
            assertTrue(actual.any{it.contentEquals(packetA)})
            assertTrue(actual.any{it.contentEquals(packetB)})
            withContext(Dispatchers.Main){a!!.reconnect()}
            withTimeout(20_000){while(a?.ready!=true||b?.ready!=true)delay(50)}
            val afterReconnect=VoiceWire.packet(1,listOf(byteArrayOf(9,8,7,6)),123)
            withContext(Dispatchers.Main){assertTrue(a!!.enqueue(afterReconnect))}
            withTimeout(10_000){while(actual.none{it.contentEquals(afterReconnect)})delay(50)}
            withContext(Dispatchers.Main){a!!.close()}
            withTimeout(10_000){while(b?.ready==true)delay(50)}
        } finally {
            withContext(Dispatchers.Main){a?.close();b?.close()}
            Tasks.await(dbA.terminate(),10,TimeUnit.SECONDS)
            Tasks.await(dbB.terminate(),10,TimeUnit.SECONDS)
            appA.delete();appB.delete()
        }
    }
}
