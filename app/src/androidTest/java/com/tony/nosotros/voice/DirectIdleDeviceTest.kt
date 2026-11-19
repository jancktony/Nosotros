package com.tony.nosotros.voice

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Production connection class, native ICE/data channel and real auth/rules; local fixtures only. */
class DirectIdleDeviceTest {
    @Test fun preparedChannelDoesNotAllocateAudioOrRenegotiateAfterOneMinuteIdle() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun backend(name: String, email: String): Pair<FirebaseApp, FirebaseFirestore> {
            val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-nosotros")
                .setApplicationId("1:123456789:android:$name").setApiKey("emulator-only-key").build(), name)
            val auth = FirebaseAuth.getInstance(app); auth.useEmulator("10.0.2.2", 9099)
            Tasks.await(auth.signInWithEmailAndPassword(email,"demo-emulator-only-123"),15,TimeUnit.SECONDS)
            val db = FirebaseFirestore.getInstance(app)
            db.firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build()).build()
            db.useEmulator("10.0.2.2",8080)
            return app to db
        }
        val (appA, dbA) = backend("direct-alice", "jancktoni@gmail.com")
        val (appB, dbB) = backend("direct-bob", "kelinherrera130323@gmail.com")
        var a: WalkieConnection? = null; var b: WalkieConnection? = null
        var statusA = ""; var statusB = ""
        var incomingListener: com.google.firebase.firestore.ListenerRegistration? = null
        val offerReady = CompletableDeferred<com.google.firebase.firestore.DocumentSnapshot>()
        val now = AtomicLong(10_000)
        val actual = ConcurrentLinkedQueue<ByteArray>()
        val pair = "voice-integration-0"
        try {
            withContext(Dispatchers.Main) {
                a = WalkieConnection(context,pair,"alice",database=dbA,ensureSession={},now={now.get()},received={actual.add(it)}) { message,_ -> statusA=message }
                b = WalkieConnection(context,pair,"bob",database=dbB,ensureSession={},now={now.get()},received={actual.add(it)}) { message,_ -> statusB=message }
                // Match the production listener instead of repeatedly opening one-shot query streams.
                incomingListener = dbB.collection("pairs").document(pair).collection("walkieCalls")
                    .whereEqualTo("to", "bob").addSnapshotListener { snapshot, error ->
                        if (!offerReady.isCompleted) {
                            if (error != null) offerReady.completeExceptionally(error)
                            else snapshot?.documents?.firstOrNull { it.getString("answer") == null }
                                ?.let { offerReady.complete(it) }
                        }
                    }
                a!!.dial("bob")
            }
            val incoming = withTimeout(15_000) { offerReady.await() }
            withContext(Dispatchers.Main) { b!!.accept(incoming) }
            withTimeout(20_000) { while (!withContext(Dispatchers.Main) { a!!.ready && b!!.ready }) delay(50) }
            val callId = withContext(Dispatchers.Main) {
                assertFalse(a!!.hasAudioResources); assertFalse(b!!.hasAudioResources)
                a!!.currentCallId
            }
            now.addAndGet(61_000)
            val fromA = VoiceWire.packet(0,listOf(byteArrayOf(1,2,3)),123)
            val fromB = VoiceWire.packet(0,listOf(byteArrayOf(4,5,6)),456)
            withContext(Dispatchers.Main) {
                a!!.releaseIdleAudio(); b!!.releaseIdleAudio()
                assertTrue("A lost prepared channel: $statusA",a!!.ready)
                assertTrue("B lost prepared channel: $statusB",b!!.ready)
                assertEquals(callId,a!!.currentCallId)
                assertTrue(a!!.sendPacket(fromA)); assertTrue(b!!.sendPacket(fromB))
            }
            withTimeout(5000) { while (actual.size < 2) delay(25) }
            assertTrue(actual.any { it.contentEquals(fromA) }); assertTrue(actual.any { it.contentEquals(fromB) })
        } finally {
            withContext(Dispatchers.Main) { incomingListener?.remove(); a?.close(); b?.close() }
            Tasks.await(dbA.waitForPendingWrites(),10,TimeUnit.SECONDS)
            Tasks.await(dbB.waitForPendingWrites(),10,TimeUnit.SECONDS)
            Tasks.await(dbA.terminate(),10,TimeUnit.SECONDS); Tasks.await(dbB.terminate(),10,TimeUnit.SECONDS)
            appA.delete(); appB.delete()
        }
    }
}
