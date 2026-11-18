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
    private var disconnected: Job? = null
    private val localIce = mutableListOf<IceCandidate>()
    private val remoteIce = mutableListOf<IceCandidate>()
    private val seenIce = mutableSetOf<String>()
    private val cleanupCandidates = mutableSetOf<DocumentReference>()
    private var published = false
    private var remoteSet = false
    private var settingAnswer = false
    @Volatile private var lastActivity = 0L
    private var closed = false
    private var epoch = 0
    private var opening = false
    private var openAttempt = 0
    val ready get() = !closed && channel?.state() == DataChannel.State.OPEN
    val active get() = pc != null || opening
    val currentCallId get() = call?.id

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }
    private fun report(message: String, ready: Boolean = false) { if (!closed) status(message, ready) }
    fun dial(partner: String) = scope.launch {
        if (active) return@launch
        opening = true
        val attempt = ++openAttempt
        try {
            withTimeout(10_000) {
            Repo.ensureSignedIn()
            val peerVersion = Repo.db.collection("pairs").document(pair).collection("members").document(partner).get().await().getString("appVersion").orEmpty()
            if (!VoiceWire.compatible(peerVersion)) { report("Actualicen ambos teléfonos a 1.10 o posterior."); return@withTimeout }
            val ref = calls.document(); call = ref
            begin(true)
            val offer = sdp { pc!!.createOffer(it, MediaConstraints()) }
            setSdp { pc!!.setLocalDescription(it, offer) }
            ref.set(mapOf("uid" to uid, "to" to partner, "offer" to offer.description,
                "createdAt" to FieldValue.serverTimestamp())).await()
            published = true; flushLocal(); listen(ref)
            }
        } catch (_: TimeoutCancellationException) { end("Se agotó la preparación del canal. Reconectando…")
        } catch (_: CancellationException) {
        } catch (_: Exception) { end("No se pudo conectar. Revisa conexión y permisos del servicio.") }
        finally { if (attempt == openAttempt) opening = false }
    }
    fun accept(document: DocumentSnapshot) = scope.launch {
        if (active) return@launch
        opening = true
        val attempt = ++openAttempt
        try {
            withTimeout(10_000) {
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
            }
        } catch (_: TimeoutCancellationException) { end("Se agotó la preparación del canal. Reconectando…")
        } catch (_: CancellationException) {
        } catch (_: Exception) { end("No se pudo aceptar la conexión.") }
        finally { if (attempt == openAttempt) opening = false }
    }
    private fun begin(outgoing: Boolean) {
        report(if (outgoing) "Llamando…" else "Conectando…")
        val generation = ++epoch
        val configuration = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
        )).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        pc = factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) { handler.post {
                if (!closed && generation == epoch) { localIce.add(candidate); flushLocal() }
            } }
            override fun onDataChannel(data: DataChannel) { handler.post {
                if (!closed && generation == epoch) attach(data) else data.dispose()
            } }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) { handler.post {
                if (closed || generation != epoch) return@post
                when (state) {
                    PeerConnection.IceConnectionState.FAILED -> end("La red bloqueó la conexión directa. Recuperando canal alternativo…")
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        disconnected?.cancel()
                        disconnected = scope.launch {
                            delay(2500)
                            if (generation == epoch) end("La conexión cambió. Recuperando el canal…")
                        }
                    }
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> {
                        disconnected?.cancel(); disconnected = null
                    }
                    else -> Unit
                }
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
        if (outgoing) attach(pc!!.createDataChannel(VoiceWire.CHANNEL, DataChannel.Init().apply {
            ordered = false; maxRetransmits = 0
        }))
        timeout = scope.launch { delay(12000); if (!ready) end("La conexión directa no está disponible. Activen el modo de redes difíciles en ambos teléfonos.") }
    }
    private fun attach(data: DataChannel) {
        if (data.label() != VoiceWire.CHANNEL) {
            data.close(); data.dispose(); end("Actualicen ambos teléfonos a 1.10 o posterior."); return
        }
        channel = data
        val generation = epoch
        fun changed() { handler.post {
            if (closed || generation != epoch) return@post
            when (data.state()) {
                DataChannel.State.OPEN -> {
                    timeout?.cancel()
                    lastActivity = android.os.SystemClock.elapsedRealtime()
                    report("Canal listo · voz comprimida", true)
                    reportSelectedPath(generation)
                    // Keep ICE/DataChannel prepared; suspend audio hardware while idle.
                    timeout = scope.launch {
                        while (true) {
                            delay(15_000)
                            if (android.os.SystemClock.elapsedRealtime() - lastActivity >= 60_000) {
                                audio?.close(); audio = null
                            }
                        }
                    }
                }
                DataChannel.State.CLOSED -> end("Conexión terminada")
                else -> Unit
            }
        } }
        data.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() { changed() }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (!buffer.binary || buffer.data.remaining() !in 11..399) return
                val packet = ByteArray(buffer.data.remaining()).also { buffer.data.get(it) }
                handler.post {
                    if (!closed && generation == epoch && ready) {
                        lastActivity = android.os.SystemClock.elapsedRealtime()
                        playback()?.receive(packet)
                    }
                }
            }
        })
        changed()
    }
    private fun playback(): PushAudio? {
        audio?.let { return it }
        val data = channel ?: return null
        if (!ready) return null
        return runCatching {
            PushAudio({ bytes ->
                data.state() == DataChannel.State.OPEN && data.bufferedAmount() < 2048 &&
                    data.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(bytes), true))
            }, { lastActivity = android.os.SystemClock.elapsedRealtime() },
                { handler.post { end("No se pudo abrir el audio. Revisa el volumen multimedia.") } })
                .also { audio = it }
        }.getOrElse { end("No se pudo abrir el audio."); null }
    }
    private fun reportSelectedPath(generation: Int) {
        pc?.getStats { report ->
            val entries = report.statsMap
            val selectedId = entries.values.firstOrNull { it.type == "transport" }
                ?.members?.get("selectedCandidatePairId") as? String
            val pairStats = selectedId?.let { entries[it] } ?: entries.values.firstOrNull {
                it.type == "candidate-pair" && it.members["state"] == "succeeded" && it.members["nominated"] == true
            }
            val ids = listOf(pairStats?.members?.get("localCandidateId"), pairStats?.members?.get("remoteCandidateId"))
            val candidates = ids.filterIsInstance<String>().mapNotNull { entries[it] }
            if (candidates.isEmpty()) return@getStats
            val relayed = candidates.any { it.members["candidateType"] == "relay" }
            handler.post { if (!closed && generation == epoch && ready)
                report(if (relayed) "Canal listo · respaldo TURN" else "Canal directo listo", true) }
        }
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
    fun talk(stopped: () -> Unit): Boolean {
        lastActivity = android.os.SystemClock.elapsedRealtime()
        return try { playback()?.start { handler.post(stopped) } ?: error("Canal desconectado"); true }
        catch (_: Exception) { stopped(); report("No se pudo usar el micrófono", ready); false }
    }
    fun stopTalking() { audio?.stop() }
    fun end(message: String = "Desconectado") {
        ++epoch; ++openAttempt; opening = false
        scope.coroutineContext.cancelChildren()
        timeout?.cancel(); disconnected?.cancel(); disconnected = null; snapshot?.remove(); iceListener?.remove()
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
