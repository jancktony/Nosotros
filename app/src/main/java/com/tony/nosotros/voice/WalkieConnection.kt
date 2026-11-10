package com.tony.nosotros.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.*
import com.tony.nosotros.data.Repo
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Signaling is private to the pair; live audio never passes through Firestore. */
class WalkieConnection(context: Context, private val pair: String, private val uid: String,
    private val status: (String, Boolean) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val factory: PeerConnectionFactory
    private val calls get() = Repo.db.collection("pairs").document(pair).collection("walkieCalls")
    private var pc: PeerConnection? = null
    private var channel: DataChannel? = null
    @Volatile private var audio: PushAudio? = null
    private var call: DocumentReference? = null
    private var snapshot: ListenerRegistration? = null
    private var iceListener: ListenerRegistration? = null
    private var timeout: Job? = null
    private val localIce = mutableListOf<IceCandidate>()
    private val remoteIce = mutableListOf<IceCandidate>()
    private val seenIce = mutableSetOf<String>()
    private val cleanupCandidates = mutableSetOf<DocumentReference>()
    private var published = false
    private var remoteSet = false
    private var settingAnswer = false
    private var closed = false
    private var epoch = 0
    val active get() = pc != null

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }
    private fun report(message: String, ready: Boolean = false) { if (!closed) status(message, ready) }
    fun dial(partner: String) = scope.launch {
        if (active) return@launch
        try {
            Repo.ensureSignedIn()
            val ref = calls.document(); call = ref
            begin(true)
            val offer = sdp { pc!!.createOffer(it, MediaConstraints()) }
            setSdp { pc!!.setLocalDescription(it, offer) }
            ref.set(mapOf("uid" to uid, "to" to partner, "offer" to offer.description,
                "createdAt" to FieldValue.serverTimestamp())).await()
            published = true; flushLocal(); listen(ref)
        } catch (_: CancellationException) {
        } catch (_: Exception) { end("No se pudo conectar. Revisa conexión y permisos del servicio.") }
    }
    fun accept(document: DocumentSnapshot) = scope.launch {
        if (active) return@launch
        try {
            Repo.ensureSignedIn()
            require(document.getString("to") == uid)
            call = document.reference
            begin(false)
            setSdp { pc!!.setRemoteDescription(it, SessionDescription(SessionDescription.Type.OFFER, document.getString("offer")!!)) }
            remoteSet = true
            val answer = sdp { pc!!.createAnswer(it, MediaConstraints()) }
            setSdp { pc!!.setLocalDescription(it, answer) }
            document.reference.update("answer", answer.description).await()
            published = true; flushLocal(); listen(document.reference)
        } catch (_: CancellationException) {
        } catch (_: Exception) { end("No se pudo aceptar la conexión.") }
    }
    private fun begin(outgoing: Boolean) {
        report(if (outgoing) "Llamando…" else "Conectando…")
        val generation = ++epoch
        val configuration = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        pc = factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) { handler.post {
                if (!closed && generation == epoch) { localIce.add(candidate); flushLocal() }
            } }
            override fun onDataChannel(data: DataChannel) { handler.post {
                if (!closed && generation == epoch) attach(data) else data.dispose()
            } }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) { handler.post {
                if (!closed && generation == epoch && state == PeerConnection.IceConnectionState.FAILED)
                    end("La red bloqueó la conexión directa. Puedes enviar una nota de voz.")
            } }
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
        }) ?: error("WebRTC no disponible")
        if (outgoing) attach(pc!!.createDataChannel("walkie-pcm16-16000", DataChannel.Init().apply {
            ordered = false; maxRetransmits = 0
        }))
        timeout = scope.launch { delay(45000); if (audio == null) end("Tu pareja no respondió o la red no permite conectar.") }
    }
    private fun attach(data: DataChannel) {
        channel = data
        val generation = epoch
        fun changed() { handler.post {
            if (closed || generation != epoch) return@post
            when (data.state()) {
                DataChannel.State.OPEN -> if (audio == null) {
                    runCatching { audio = PushAudio(data) }.onSuccess {
                        timeout?.cancel(); report("Conectados · mantén pulsado para hablar", true)
                        timeout = scope.launch { delay(300000); end("Sesión terminada. Puedes volver a conectar.") }
                    }.onFailure { end("No se pudo abrir el audio.") }
                }
                DataChannel.State.CLOSED -> end("Conexión terminada")
                else -> Unit
            }
        } }
        data.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() { changed() }
            override fun onMessage(buffer: DataChannel.Buffer) { audio?.receive(buffer) }
        })
        changed()
    }
    private fun listen(ref: DocumentReference) {
        val generation = epoch
        snapshot = ref.addSnapshotListener { doc, error ->
            if (closed || generation != epoch) return@addSnapshotListener
            if (error != null) { end("Se perdió el acceso al canal"); return@addSnapshotListener }
            if (doc?.exists() != true) { end("Tu pareja cerró el canal"); return@addSnapshotListener }
            val answer = doc.getString("answer")
            if (!remoteSet && !settingAnswer && answer != null) {
                settingAnswer = true
                scope.launch {
                    try {
                        setSdp { pc!!.setRemoteDescription(it, SessionDescription(SessionDescription.Type.ANSWER, answer)) }
                        remoteSet = true; flushRemote()
                    } catch (_: CancellationException) {
                    } catch (_: Exception) { end("No se pudo negociar el canal") }
                }
            }
        }
        iceListener = ref.collection("candidates").addSnapshotListener { docs, error ->
            if (closed || generation != epoch) return@addSnapshotListener
            if (error != null) { end("No se pudo abrir el canal privado"); return@addSnapshotListener }
            docs?.documents?.filter { it.getString("uid") != uid && seenIce.add(it.id) }?.forEach {
                cleanupCandidates.add(it.reference)
                remoteIce.add(IceCandidate(it.getString("mid"), (it.getLong("line") ?: 0).toInt(), it.getString("candidate")))
            }
            flushRemote()
        }
    }
    private fun flushRemote() { if (remoteSet) { remoteIce.forEach { pc?.addIceCandidate(it) }; remoteIce.clear() } }
    private fun flushLocal() {
        val ref = call ?: return
        if (!published) return
        localIce.toList().forEach {
            val candidateRef = ref.collection("candidates").document()
            cleanupCandidates.add(candidateRef)
            candidateRef.set(mapOf("uid" to uid, "candidate" to it.sdp,
                "mid" to (it.sdpMid ?: ""), "line" to it.sdpMLineIndex))
                .addOnFailureListener { if (call == ref) end("No se pudo publicar el canal privado") }
        }
        localIce.clear()
    }
    fun talk(stopped: () -> Unit) {
        try { audio?.start { handler.post(stopped) } ?: error("Canal desconectado") }
        catch (_: Exception) { report("No se pudo usar el micrófono", audio != null); stopped() }
    }
    fun stopTalking() { audio?.stop() }
    fun end(message: String = "Desconectado") {
        ++epoch
        scope.coroutineContext.cancelChildren()
        timeout?.cancel(); snapshot?.remove(); iceListener?.remove()
        snapshot = null; iceListener = null
        audio?.close(); audio = null
        channel?.unregisterObserver(); channel?.close(); channel?.dispose(); channel = null
        pc?.close(); pc?.dispose(); pc = null
        val old = call; call = null
        if (old != null) {
            val batch = Repo.db.batch()
            cleanupCandidates.take(400).forEach { batch.delete(it) }
            batch.delete(old); batch.commit()
        }
        cleanupCandidates.clear()
        published = false; remoteSet = false; settingAnswer = false
        localIce.clear(); remoteIce.clear(); seenIce.clear()
        report(message)
    }
    fun close() { end(); closed = true; scope.cancel(); factory.dispose() }

    private suspend fun sdp(action: (SdpObserver) -> Unit): SessionDescription = suspendCancellableCoroutine { continuation ->
        action(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { if (continuation.isActive) continuation.resume(sdp) }
            override fun onCreateFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String) = Unit
        })
    }
    private suspend fun setSdp(action: (SdpObserver) -> Unit): Unit = suspendCancellableCoroutine { continuation ->
        action(object : SdpObserver {
            override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
            override fun onSetFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
            override fun onCreateSuccess(sdp: SessionDescription) = Unit
            override fun onCreateFailure(error: String) = Unit
        })
    }
}
