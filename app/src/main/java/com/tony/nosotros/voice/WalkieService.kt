package com.tony.nosotros.voice

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tony.nosotros.App
import com.tony.nosotros.R
import com.tony.nosotros.data.Feature
import com.tony.nosotros.data.FeatureAccess
import com.tony.nosotros.data.Repo
import com.tony.nosotros.ui.MainActivity

/** Opt-in background reception; microphone is only used by a visible activity's press. */
class WalkieService : Service() {
    private var connection: WalkieConnection? = null
    private var relay: WalkieRelay? = null
    private var stream: SocketVoiceRelay? = null
    private enum class Path { DIRECT, STREAM, FRAGMENTS }
    private var activePath: Path? = null
    private var microphoneEpoch = 0
    private var pairId = ""
    private var relayPermitted = false
    private var relayPartner: String? = null
    private var pairListener: ListenerRegistration? = null
    private var callsListener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var partner: String? = null
    private var retryAttempt = 0
    private val retry = Runnable { if (App.instance.prefs.receiveWalkie) { if (requested) talk() else if (WalkieHub.foregroundOpen) prepare() } }
    private val clock = VoiceClock { SystemClock.elapsedRealtime() }
    private var pendingCalls: com.google.firebase.firestore.QuerySnapshot? = null
    private var destroyed = false
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private val connectivity by lazy { getSystemService(android.net.ConnectivityManager::class.java) }
    private val clockRetry = Runnable { synchronizeClock() }
    private var requested = false
    private var uid = ""
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val focus by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change -> if (change < 0) {
                stopTalking(); connection?.end("Audio interrumpido. El canal está en espera.")
            } }, handler).build()
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { App.instance.prefs.receiveWalkie = false; stopSelf(); return START_NOT_STICKY }
        if (!Repo.authorizedSession || !App.instance.prefs.receiveWalkie) { stopSelf(); return START_NOT_STICKY }
        if (WalkieHub.service === this) { resumeReception(); return START_STICKY }
        uid = Repo.uid ?: run { stopSelf(); return START_NOT_STICKY }
        val pair = App.instance.prefs.pairId ?: run { stopSelf(); return START_NOT_STICKY }
        pairId = pair
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Recepción de voz en segundo plano", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        WalkieHub.service = this
        WalkieHub.state.value = WalkieHub.State(enabled = true, message = "En espera · pulsa para hablar")
        try {
            connection = WalkieConnection(this, pair, uid) { message, _ -> channelChanged(message) }
        } catch (_: Exception) {
            channelChanged("Preparando el canal privado…")
        } catch (_: LinkageError) {
            channelChanged("Preparando el canal privado…")
        }
        authListener = FirebaseAuth.AuthStateListener { auth -> if (auth.currentUser?.uid != uid || !Repo.authorizedSession) stopSelf() }
            .also { FirebaseAuth.getInstance().addAuthStateListener(it) }
        pairListener = Repo.db.collection("pairs").document(pair).addSnapshotListener { snapshot, error ->
            val members = (snapshot?.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
            val modes = (snapshot?.get("featureFlags") as? Map<*, *>)?.entries?.mapNotNull { (k,v) -> if(k is String && v is String) k to v else null }?.toMap().orEmpty()
            if (error != null || uid !in members || !FeatureAccess(modes, snapshot?.getString("admin") == uid).enabled(Feature.WALKIE)) { stopSelf(); return@addSnapshotListener }
            partner = members.firstOrNull { it != uid }
            relayPermitted = FeatureAccess(modes, snapshot?.getString("admin") == uid).enabled(Feature.WALKIE_RELAY)
            refreshRelay()
            if (requested && clock.ready) talk() else if (WalkieHub.foregroundOpen) prepare()
        }
        callsListener = Repo.db.collection("pairs").document(pair).collection("walkieCalls")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(10).addSnapshotListener { snapshot, error ->
                if (error != null) { connection?.end("No se pudo acceder al canal privado"); return@addSnapshotListener }
                pendingCalls = snapshot
                acceptPendingCall()
            }
        networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { handler.post {
                if (!destroyed) {
                    relay?.reconnect(); stream?.reconnect()
                    if (connection?.ready != true) {
                        val held = requested
                        connection?.end("Recuperando conexión…")
                        requested = held
                        synchronizeClock()
                    }
                }
            } }
        }.also { runCatching { connectivity.registerDefaultNetworkCallback(it) } }
        synchronizeClock()
        // Preserve the user’s reception choice if Android reclaims the service process.
        return START_STICKY
    }
    private fun synchronizeClock() {
        if (destroyed) return
        val own = Repo.db.collection("pairs").document(pairId).collection("members").document(uid)
        own.set(mapOf("voiceClockAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge())
            .continueWithTask { task -> task.result; own.get(com.google.firebase.firestore.Source.CACHE) }
            .addOnSuccessListener { doc ->
                if (destroyed) return@addOnSuccessListener
                val stamp = doc.getTimestamp("voiceClockAt")?.toDate()?.time
                if (stamp == null) {
                    handler.postDelayed(clockRetry, 2000); return@addOnSuccessListener
                }
                clock.synchronize(stamp)
                acceptPendingCall()
                if (requested) talk() else if (WalkieHub.foregroundOpen) prepare()
            }.addOnFailureListener {
                if (!destroyed) {
                    WalkieHub.state.value = WalkieHub.state.value.copy(message = "No se pudo preparar el canal privado. Revisa internet.")
                    handler.removeCallbacks(clockRetry); handler.postDelayed(clockRetry, 5000)
                }
            }
    }
    private fun acceptPendingCall() {
        if (!clock.ready || destroyed) return
        val incoming = pendingCalls?.documents?.firstOrNull {
            it.getString("to") == uid && it.getString("answer") == null &&
                clock.fresh(it.getTimestamp("createdAt")?.toDate()?.time ?: 0, 45_000)
        } ?: return
        val current = connection ?: return
        if (current.currentCallId == incoming.id) return
        if (current.active) {
            // Deterministic resolution when both press simultaneously.
            if (incoming.getString("uid").orEmpty() >= uid) return
            val held = requested
            current.end()
            requested = held
        }
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        current.accept(incoming)

    }
    private fun ready(path: Path): Boolean = when (path) {
        Path.DIRECT -> connection?.ready == true
        Path.STREAM -> stream?.ready == true
        Path.FRAGMENTS -> relay?.ready == true
    }
    private fun choosePath(): Path? {
        activePath?.takeIf { ready(it) }?.let { return it }
        return listOf(Path.DIRECT, Path.STREAM, Path.FRAGMENTS).firstOrNull { ready(it) }
    }
    private fun channelChanged(message: String) {
        if (destroyed) return
        if (activePath?.let { !ready(it) } == true) haltAudio()
        val selected = choosePath()
        val available = selected != null
        val text = if (message.startsWith("No se pudo")) message else when (selected) {
            Path.DIRECT -> "Canal directo listo"
            Path.STREAM -> "Canal privado listo"
            Path.FRAGMENTS -> "Canal alternativo listo"
            null -> message
        }
        WalkieHub.state.value = WalkieHub.state.value.copy(enabled = true, connected = available, message = text)
        if (available) {
            retryAttempt = 0; handler.removeCallbacks(retry)
            if (requested) startTalking()
        } else if ((WalkieHub.foregroundOpen || requested) && connection?.active != true && retryAttempt < 3) {
            handler.removeCallbacks(retry); retryAttempt++
            handler.postDelayed(retry, 1500L * retryAttempt)
        }
    }
    fun refreshRelay() {
        val enabled = relayPermitted && App.instance.prefs.allowVoiceRelay && partner != null
        if (!enabled) {
            if (relay != null || stream != null) {
                stopTalking(); relay?.close(); stream?.close()
                relay = null; stream = null; relayPartner = null
                channelChanged("En espera · pulsa para hablar")
            }
            return
        }
        if (relay != null && stream != null && relayPartner == partner) {
            if (stream?.ready != true) stream?.reconnect()
            return
        }
        relay?.close(); stream?.close(); relayPartner = partner
        relay = WalkieRelay(pairId, uid, partner!!, clock = clock,
            focus = { audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }) { message, _ ->
            channelChanged(message)
        }.also { it.start() }
        stream = SocketVoiceRelay(pairId, uid, clock,
            focus = { audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }) { message, _ ->
            channelChanged(message)
        }.also { it.start() }
    }
    fun resumeReception() {
        if (destroyed || !App.instance.prefs.receiveWalkie || !Repo.authorizedSession) return
        refreshRelay()
        relay?.reconnect()
        if (stream?.ready != true) stream?.reconnect()
    }
    fun reconnect() {
        retryAttempt = 0; handler.removeCallbacks(retry); stopTalking(); connection?.end("Reconectando…"); refreshRelay(); relay?.reconnect(); stream?.reconnect()
        // The elected warmer only applies to passive preparation, never to a user's reconnect action.
        if (clock.ready) partner?.let { if (connection?.active != true) connection?.dial(it) }
    }
    fun prepare() {
        if (!clock.ready) return
        val current = connection ?: return
        val to = partner ?: return
        // One elected warmer avoids two simultaneous offers. A deliberate press may still call either way.
        if (!current.active && uid < to) current.dial(to)
    }
    fun talk() {
        if (!requested) retryAttempt = 0
        requested = true
        relay?.reconnect()
        if (choosePath() != null) { startTalking(); return }
        if (!clock.ready) { WalkieHub.state.value = WalkieHub.state.value.copy(message = "Preparando canal privado…"); return }
        val current = connection ?: return
        if (!current.active) {
            val to = partner ?: run { WalkieHub.state.value = WalkieHub.state.value.copy(message = "Cargando el canal…"); return }
            current.dial(to)
            // The mic starts only if the finger is still pressed when the channel becomes ready.
            requested = true
        }
    }
    private fun startTalking() {
        if (!requested || WalkieHub.state.value.talking) return
        val selected = choosePath() ?: return
        if (!MainActivity.visibleForMicrophone() || androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requested = false
            WalkieHub.state.value = WalkieHub.state.value.copy(message = "Abre Voz y concede permiso de micrófono para hablar")
            return
        }
        try {
            ServiceCompat.startForeground(this, ID, notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (_: Exception) {
            requested = false
            WalkieHub.state.value = WalkieHub.state.value.copy(message = "Android no permitió abrir el micrófono. Abre la app y revisa el permiso.")
            return
        }
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stopTalking(); return
        }
        val token = ++microphoneEpoch
        activePath = selected
        val stopped = {
            if (token == microphoneEpoch) {
            releaseMicrophoneType()
            activePath = null
            requested = false
            WalkieHub.state.value = WalkieHub.state.value.copy(talking = false,
                message = if (WalkieHub.state.value.connected) "Canal listo · mantén pulsado" else "En espera")
            }
        }
        val started = when (selected) {
            Path.DIRECT -> connection?.talk(stopped) == true
            Path.STREAM -> stream?.talk(stopped) == true
            Path.FRAGMENTS -> relay?.talk(stopped) == true
        }
        if (!started) { activePath = null; requested = false; releaseMicrophoneType() }
        WalkieHub.state.value = WalkieHub.state.value.copy(talking = started,
            message = if (started) "Hablando · voz de bajo consumo" else "Aún no hay canal. Espera la señal antes de hablar.")
    }
    fun stopTalking() { requested = false; haltAudio() }
    private fun haltAudio() {
        ++microphoneEpoch; activePath = null
        connection?.stopTalking(); relay?.stopTalking(); stream?.stopTalking(); releaseMicrophoneType()
        WalkieHub.state.value = WalkieHub.state.value.copy(talking = false,
            message = if (WalkieHub.state.value.connected) "Canal listo · mantén pulsado" else WalkieHub.state.value.message)
    }
    private fun releaseMicrophoneType() {
        runCatching { ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) }
    }
    override fun onDestroy() {
        destroyed = true
        networkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }; networkCallback = null
        handler.removeCallbacks(clockRetry); handler.removeCallbacks(retry); stopTalking(); pairListener?.remove(); callsListener?.remove()
        authListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
        relay?.close(); relay = null
        stream?.close(); stream = null
        connection?.close(); connection = null
        audioManager.abandonAudioFocusRequest(focus)
        WalkieHub.service = null
        WalkieHub.state.value = WalkieHub.State()
        super.onDestroy()
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 91, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 92, Intent(this, WalkieService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Nosotros activo").setContentText("Funciones en segundo plano activas")
            .setContentIntent(open).setOngoing(true).setSilent(true).setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Desactivar", stop).build()
    }
    companion object { private const val ID = 7; private const val CHANNEL = App.CH_TRACKING }
}
