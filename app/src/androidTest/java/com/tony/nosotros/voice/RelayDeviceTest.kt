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
  repeat(3) { round ->
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  fun backend(name:String,email:String):Pair<FirebaseApp,FirebaseFirestore>{
   val app=FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setProjectId("demo-nosotros")
    .setApplicationId("1:123456789:android:"+name).setApiKey("emulator-only-key").build(),name)
   val auth=FirebaseAuth.getInstance(app);auth.useEmulator("10.0.2.2",9099)
   Tasks.await(auth.signInWithEmailAndPassword(email,"demo-emulator-only-123"),15,TimeUnit.SECONDS)
   val db=FirebaseFirestore.getInstance(app);db.useEmulator("10.0.2.2",8080)
   return app to db
  }
  val (appA,dbA)=backend("relay-alice-$round","jancktoni@gmail.com")
  val (appB,dbB)=backend("relay-bob-$round","kelinherrera130323@gmail.com")
  var a:WalkieRelay?=null;var b:WalkieRelay?=null
  var statusA="";var statusB=""
  val clockA=VoiceClock{android.os.SystemClock.elapsedRealtime()};val clockB=VoiceClock{android.os.SystemClock.elapsedRealtime()}
  assertEquals("alice",FirebaseAuth.getInstance(appA).currentUser?.uid)
  assertEquals("bob",FirebaseAuth.getInstance(appB).currentUser?.uid)
  assertTrue(FirebaseAuth.getInstance(appA).currentUser?.isEmailVerified==true)
  assertNotNull(Tasks.await(dbA.document("pairs/voice-integration-$round").get(com.google.firebase.firestore.Source.SERVER),10,TimeUnit.SECONDS).get("members"))
  val arrival=CountDownLatch(2);val actual=mutableListOf<ByteArray>()
  val packetA=VoiceWire.packet(0,listOf(byteArrayOf(1,2,3,4)),123)
  val packetB=VoiceWire.packet(0,listOf(byteArrayOf(5,6,7,8)),456)
  try {
   withContext(Dispatchers.Main){
    a=WalkieRelay("voice-integration-$round","alice","bob",clock=clockA,database=dbA,received={actual.add(it);arrival.countDown()},focus={true}){message,_->statusA=message}
    b=WalkieRelay("voice-integration-$round","bob","alice",clock=clockB,database=dbB,received={actual.add(it);arrival.countDown()},focus={true}){message,_->statusB=message}
    a!!.start();b!!.start()
   }
   try { withTimeout(20_000){while(a?.ready!=true||b?.ready!=true)delay(50)} }
   catch(e:kotlinx.coroutines.TimeoutCancellationException) {
    val pa=Tasks.await(dbA.document("pairs/voice-integration-$round/walkiePresence/bob").get(com.google.firebase.firestore.Source.SERVER),10,TimeUnit.SECONDS)
    val pb=Tasks.await(dbB.document("pairs/voice-integration-$round/walkiePresence/alice").get(com.google.firebase.firestore.Source.SERVER),10,TimeUnit.SECONDS)
    fail("Transport readiness: A="+statusA+" B="+statusB+" clockA="+clockA.ready+" clockB="+clockB.ready+" peerA="+pa.data+" peerB="+pb.data)
   }
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
}
