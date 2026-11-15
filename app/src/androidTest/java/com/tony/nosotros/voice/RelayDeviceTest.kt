package com.tony.nosotros.voice

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Both authenticated identities, real rules, server timestamps and delivery. Local emulators only. */
class RelayDeviceTest {
 @Test fun twoConsentingAccountsExchangePacketsThroughFirestore()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  fun backend(name:String,email:String):Pair<FirebaseApp,FirebaseFirestore>{
   val app=FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setProjectId("demo-nosotros")
    .setApplicationId("1:123456789:android:"+name).setApiKey("emulator-only-key").build(),name)
   val auth=FirebaseAuth.getInstance(app);auth.useEmulator("10.0.2.2",9099)
   Tasks.await(auth.signInWithEmailAndPassword(email,"demo-emulator-only-123"),15,TimeUnit.SECONDS)
   val db=FirebaseFirestore.getInstance(app);db.useEmulator("10.0.2.2",8080)
   return app to db
  }
  val (appA,dbA)=backend("relay-alice","jancktoni@gmail.com")
  val (appB,dbB)=backend("relay-bob","kelinherrera130323@gmail.com")
  var a:WalkieRelay?=null;var b:WalkieRelay?=null
  val arrival=CountDownLatch(2);val actual=mutableListOf<ByteArray>()
  val packetA=VoiceWire.packet(0,listOf(byteArrayOf(1,2,3,4)),123)
  val packetB=VoiceWire.packet(0,listOf(byteArrayOf(5,6,7,8)),456)
  try {
   withContext(Dispatchers.Main){
    a=WalkieRelay("voice-integration","alice","bob",database=dbA,received={actual.add(it);arrival.countDown()},focus={true}){_,_->}
    b=WalkieRelay("voice-integration","bob","alice",database=dbB,received={actual.add(it);arrival.countDown()},focus={true}){_,_->}
    a!!.start();b!!.start()
   }
   withTimeout(20_000){while(a?.ready!=true||b?.ready!=true)delay(50)}
   withContext(Dispatchers.Main){
    assertTrue(a!!.enqueue(packetA));a!!.flush()
    assertTrue(b!!.enqueue(packetB));b!!.flush()
   }
   assertTrue("No delivery through authenticated Firestore rules",arrival.await(10,TimeUnit.SECONDS))
   assertTrue(actual.any{it.contentEquals(packetA)});assertTrue(actual.any{it.contentEquals(packetB)})
  } finally {
   withContext(Dispatchers.Main){a?.close();b?.close()}
   Tasks.await(dbA.terminate(),10,TimeUnit.SECONDS);Tasks.await(dbB.terminate(),10,TimeUnit.SECONDS)
   appA.delete();appB.delete()
  }
 }
}
