package com.tony.nosotros.voice

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Fictitious accounts, local emulators and synthetic bytes: no recording or production access. */
class NoteDeliveryDeviceTest {
 @Test fun offlineDraftRetriesOnceAndOnlyReceiverConfirmsReceipt()=runBlocking<Unit> {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  fun backend(name:String,email:String):Pair<FirebaseApp,FirebaseFirestore>{
   val app=FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setProjectId("demo-nosotros")
    .setApplicationId("1:123456789:android:"+name).setApiKey("emulator-only-key").build(),name)
   val auth=FirebaseAuth.getInstance(app);auth.useEmulator("10.0.2.2",9099)
   Tasks.await(auth.signInWithEmailAndPassword(email,"demo-emulator-only-123"),15,TimeUnit.SECONDS)
   return app to FirebaseFirestore.getInstance(app).apply{firestoreSettings=FirebaseFirestoreSettings.Builder()
    .setHost("10.0.2.2:8080").setSslEnabled(false).setPersistenceEnabled(false).build()}
  }
  val a=backend("notes-alice","jancktoni@gmail.com");val b=backend("notes-bob","kelinherrera130323@gmail.com")
  val pair="notes-integration";val id=java.util.UUID.randomUUID().toString();val draft=VoiceNotes.Draft(id,1200)
  val key=java.security.MessageDigest.getInstance("SHA-256").digest("$pair:alice".toByteArray()).joinToString(""){"%02x".format(it)}
  val target=File(context.filesDir,"voice-drafts/$key/${id}_1200.m4a");target.parentFile!!.mkdirs()
  val payload=byteArrayOf(1,2,3,4)
  val sender=VoiceNotes(context,pair,a.second,{"alice"},{"alice"}){"alice"}
  val receiver=VoiceNotes(context,pair,b.second,{"bob"},{"bob"}){"bob"}
  var sentListener:com.google.firebase.firestore.ListenerRegistration?=null
  var receivedListener:com.google.firebase.firestore.ListenerRegistration?=null
  try{
   target.writeBytes(payload);a.second.disableNetwork().await()
   var failed=false;try{withTimeout(15000){sender.sendDraft(draft)}}catch(expected:Exception){failed=true}
   assertTrue(failed);assertTrue(target.exists())
   a.second.enableNetwork().await();withTimeout(20000){sender.sendDraft(draft)}
   assertFalse(target.exists())
   val ref=a.second.collection("pairs").document(pair).collection("voiceNotes").document(id)
   assertNull(ref.get(Source.SERVER).await().getString("receivedBy"))
   val received=CountDownLatch(1)
   sentListener=sender.listen({notes->if(notes.any{it.id==id&&it.received})received.countDown()},{})
   receivedListener=receiver.listen({}, {})
   assertTrue("Receipt must come back from the receiver",received.await(15,TimeUnit.SECONDS))
   assertEquals("bob",ref.get(Source.SERVER).await().getString("receivedBy"))
   target.writeBytes(payload);withTimeout(20000){sender.sendDraft(draft)}
   assertFalse(target.exists());assertEquals(1,a.second.collection("pairs").document(pair).collection("voiceNotes").get(Source.SERVER).await().documents.count{it.id==id})
   ref.delete().await()
  }finally{
   sentListener?.remove();receivedListener?.remove();sender.close();receiver.close();target.delete();a.first.delete();b.first.delete()
  }
 }
}
