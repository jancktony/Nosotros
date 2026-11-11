package com.tony.nosotros.voice

import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.*
import com.tony.nosotros.data.Repo
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentLinkedQueue

/** Explicit opt-in fallback for NAT-blocked networks. Private, short voice fragments,
 * not a public TURN service. Both phones must consent. Clients delete read/expired fragments;
 * if both are killed, cleanup resumes on next start (no paid Firestore TTL). */
class WalkieRelay(private val pair: String, private val uid: String, private val partner: String,
                  private val focus: () -> Boolean, private val status: (String, Boolean) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val root get() = Repo.db.collection("pairs").document(pair)
    private val presence get() = root.collection("walkiePresence")
    private val packets get() = root.collection("walkiePackets")
    private var peer: ListenerRegistration? = null
    private var incoming: ListenerRegistration? = null
    private var ownReady = false
    private var peerAt = 0L
    private var peerEnabled = false
    private var closed = false
    private var engine: PushAudio? = null
    private var audioJob: Job? = null
    private var lastActivity = 0L
    private var inFlight = false
    private val pending = ConcurrentLinkedQueue<ByteArray>()
    private val written = mutableSetOf<DocumentReference>()
    private val seen = LinkedHashSet<String>()
    val ready get() = !closed && ownReady && peerEnabled && System.currentTimeMillis() - peerAt in 0..480_000L
    fun start() {
        peer = presence.document(partner).addSnapshotListener { d, error ->
            peerEnabled = error == null && d?.getBoolean("relay") == true && d.getString("protocol") == VoiceWire.CHANNEL
            peerAt = d?.getTimestamp("updatedAt")?.toDate()?.time ?: 0
            report()
        }
        incoming = packets.whereEqualTo("to", uid).limit(30).addSnapshotListener { snapshot, error ->
            if (closed) return@addSnapshotListener
            if (error != null) { status("No se pudo abrir el modo de redes difíciles", false); return@addSnapshotListener }
            snapshot?.documents?.sortedBy { it.getTimestamp("createdAt")?.toDate()?.time ?: 0 }?.forEach { d ->
                if (d.metadata.isFromCache || !seen.add(d.id)) return@forEach
                while (seen.size > 128) seen.remove(seen.first())
                val at = d.getTimestamp("createdAt")?.toDate()?.time ?: 0
                val bytes = d.getBlob("audio")?.toBytes()
                if (ready && d.getString("uid") == partner && System.currentTimeMillis() - at in 0..6000 && bytes != null && focus()) {
                    VoiceWire.unbundle(bytes)?.forEach { audio().receive(it) }
                    lastActivity = android.os.SystemClock.elapsedRealtime()
                }
                d.reference.delete()
            }
        }
        scope.launch {
            while (isActive) {
                runCatching {
                    presence.document(uid).set(mapOf("relay" to true, "protocol" to VoiceWire.CHANNEL,
                        "updatedAt" to FieldValue.serverTimestamp())).await()
                    ownReady = true; report()
                    // Restart cleanup of fragments left by an interrupted sender.
                    packets.whereEqualTo("uid", uid).limit(100).get().await().documents.forEach {
                        if (System.currentTimeMillis() - (it.getTimestamp("createdAt")?.toDate()?.time ?: 0) > 15_000) it.reference.delete()
                    }
                }.onFailure { ownReady = false; report() }
                delay(240_000)
            }
        }
    }
    private fun report() { if (!closed) status(if (ready) "Red lenta · voz comprimida lista" else "Esperando que ambos activen redes difíciles", ready) }
    private fun audio(): PushAudio {
        engine?.let { return it }
        val audio = PushAudio({ bytes ->
            if (!ready) false else {
                while (pending.size >= 8) pending.poll()
                pending.offer(bytes); true
            }
        }, { lastActivity = android.os.SystemClock.elapsedRealtime() },
            { handler.post { status("No se pudo abrir audio. Revisa permiso de micrófono y volumen multimedia.", false) } })
        engine = audio; lastActivity = android.os.SystemClock.elapsedRealtime()
        audioJob = scope.launch {
            while (isActive && engine != null) {
                delay(500); flush()
                if (android.os.SystemClock.elapsedRealtime() - lastActivity >= 60_000) {
                    engine?.close(); engine = null; pending.clear(); break
                }
            }
        }
        return audio
    }
    private fun flush() {
        if (!ready || inFlight || pending.isEmpty()) return
        val list = mutableListOf<ByteArray>()
        while (list.size < 8) list.add(pending.poll() ?: break)
        if (list.isEmpty()) return
        val ref = packets.document(); written.add(ref); inFlight = true
        ref.set(mapOf("uid" to uid, "to" to partner, "audio" to Blob.fromBytes(VoiceWire.bundle(list)),
            "createdAt" to FieldValue.serverTimestamp(), "expiresAt" to System.currentTimeMillis() + 15_000))
            .addOnSuccessListener { inFlight = false }
            .addOnFailureListener { inFlight = false; pending.clear(); status("No se pudo enviar. Revisa la conexión o la cuota gratuita.", false) }
        scope.launch { delay(15_000); ref.delete(); written.remove(ref) }
    }
    fun talk(stopped: () -> Unit): Boolean {
        if (!ready) return false
        return try { audio().start { handler.post { flush(); stopped() } }; true }
        catch (_: Exception) { status("No se pudo usar el micrófono", ready); false }
    }
    fun stopTalking() { engine?.stop(); flush() }
    fun close() {
        closed = true; peer?.remove(); incoming?.remove(); scope.cancel()
        engine?.close(); engine = null; pending.clear()
        written.take(100).forEach { it.delete() }; written.clear()
        presence.document(uid).set(mapOf("relay" to false, "protocol" to VoiceWire.CHANNEL,
            "updatedAt" to FieldValue.serverTimestamp()))
    }
}
