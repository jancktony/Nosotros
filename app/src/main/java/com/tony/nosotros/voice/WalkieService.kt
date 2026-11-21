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
    private var transmittingDirect = false
    private var microphoneEpoch = 0L
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
        if (connection != null) { refreshRelay(); relay?.reconnect(); return START_STICKY }
        uid = Repo.uid ?: run { stopSelf(); return START_NOT_STICKY }
        val pair = App.instance.prefs.pairId ?: run { stopSelf(); return START_NOT_STICKY }
        pairId = pair
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Recepción de voz en segundo plano", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        WalkieHub.service = this
        WalkieHub.state.value = WalkieHub.State(enabled = true, message = "En espera · pulsa para hablar")
        try {
            connection = WalkieConnection(this, pair, uid,
                focus = { audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }) { message, ready ->
                if (destroyed) return@WalkieConnection
                val available = ready || relay?.ready == true
                if (!ready && transmittingDirect && WalkieHub.state.value.talking) {
                    val held = requested; stopTalking(); requested = held
                }
                // Connecting is not a release gesture: keep a held press pending until OPEN.
                if (!available && connection?.active != true) {
                    // A transport failure is not the user's release gesture.
                    val held = requested
                    stopTalking(); requested = held
                    audioManager.abandonAudioFocusRequest(focus)
                }
                WalkieHub.state.value = WalkieHub.state.value.copy(enabled = true, connected = available,
                    message = if (!ready && relay != null) { if (relay?.ready == true) "Red lenta · voz comprimida lista" else "Preparando canal alternativo · esperando recepción de ambos" } else message)
                if (ready) {
                    retryAttempt = 0; handler.removeCallbacks(retry)
                    if (requested) startTalking()
                } else if (!available && (WalkieHub.foregroundOpen || requested) && connection?.active != true && !message.contains("inactividad")) {
                    handler.removeCallbacks(retry); retryAttempt = (retryAttempt + 1).coerceAtMost(5)
                    handler.postDelayed(retry, WalkieRetry.delayMillis(retryAttempt))
                }
                if (available && requested) startTalking()
            }
        } catch (_: Exception) { WalkieHub.state.value = WalkieHub.State(message = "No se pudo abrir el walkie"); stopSelf(); return START_NOT_STICKY }
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
                    relay?.reconnect()
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
        current.accept(incoming)

    }
    fun refreshRelay() {
        val enabled = relayPermitted && App.instance.prefs.allowVoiceRelay && partner != null
        if (!enabled) {
            if (relay != null) {
                stopTalking(); relay?.close(); relay = null; relayPartner = null
                val direct = connection?.ready == true
                WalkieHub.state.value = WalkieHub.state.value.copy(connected = direct,
                    message = if (direct) "Canal directo listo" else "En espera · pulsa para hablar")
            }
            return
        }
        if (relay != null && relayPartner == partner) return
        relay?.close(); relayPartner = partner
        relay = WalkieRelay(pairId, uid, partner!!, clock = clock,
            focus = { audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }) { message, ready ->
            if (!ready && !transmittingDirect && WalkieHub.state.value.talking) {
                val held = requested; stopTalking(); requested = held
            }
            if (connection?.ready != true) {
                WalkieHub.state.value = WalkieHub.state.value.copy(connected = ready, message = message)
                if (!ready && WalkieHub.state.value.talking) {
                    val held = requested; stopTalking(); requested = held
                } else if (ready && requested) startTalking()
            } else if (requested) startTalking()
        }.also { it.start() }
    }
    fun resumeReception() {
        if (destroyed || !App.instance.prefs.receiveWalkie || !Repo.authorizedSession) return
        refreshRelay()
        relay?.reconnect()
    }
    fun reconnect() {
        retryAttempt = 0; handler.removeCallbacks(retry); stopTalking(); connection?.end("Reconectando…"); refreshRelay(); relay?.reconnect()
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
        val current = connection ?: return
        if (!requested) retryAttempt = 0
        requested = true
        relay?.reconnect()
        if (!clock.ready) { WalkieHub.state.value = WalkieHub.state.value.copy(message = "Preparando canal privado…"); return }
        if (connection?.ready == true || relay?.ready == true) startTalking()
        else if (!current.active) {
            val to = partner ?: run { WalkieHub.state.value = WalkieHub.state.value.copy(message = "Cargando el canal…"); return }
            current.dial(to)
            // The mic starts only if the finger is still pressed when the channel becomes ready.
            requested = true
        }
    }
    private fun startTalking() {
        if (!requested || WalkieHub.state.value.talking) return
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
        val epoch = ++microphoneEpoch
        val stopped = stopped@{
            if (epoch != microphoneEpoch || destroyed) return@stopped
            releaseMicrophoneType()
            requested = false
            WalkieHub.state.value = WalkieHub.state.value.copy(talking = false,
                message = if (WalkieHub.state.value.connected) "Canal listo · mantén pulsado" else "En espera")
        }
        transmittingDirect = connection?.ready == true
        val started = if (transmittingDirect) connection?.talk(stopped) == true else relay?.talk(stopped) == true
        if (!started) releaseMicrophoneType()
        if (started) WalkieHub.state.value = WalkieHub.state.value.copy(talking = true,
            message = "Hablando · voz de bajo consumo")
    }
    fun stopTalking() {
        ++microphoneEpoch
        requested = false
        connection?.stopTalking(); relay?.stopTalking(); releaseMicrophoneType()
        WalkieHub.state.value = WalkieHub.state.value.copy(talking = false,
            message = if (WalkieHub.state.value.connected) "Canal listo · mantén pulsado" else WalkieHub.state.value.message)
    }
    private fun releaseMicrophoneType() {
        if (destroyed) return
        runCatching { ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) }
    }
    override fun onDestroy() {
        destroyed = true
        networkCallback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }; networkCallback = null
        handler.removeCallbacks(clockRetry); handler.removeCallbacks(retry); stopTalking(); pairListener?.remove(); callsListener?.remove()
        authListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
        relay?.close(); relay = null
        connection?.close(); connection = null
        audioManager.abandonAudioFocusRequest(focus)
        WalkieHub.service = null
        WalkieHub.state.value = WalkieHub.State()
        com.tony.nosotros.tracking.TrackingService.running?.updateNotification()
        super.onDestroy()
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 91, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 92, Intent(this, WalkieService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Recepción de voz activada").setContentText("Toca para abrir Nosotros")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Desactivar", stop).build()
    }
    companion object { private const val ID = 7; private const val CHANNEL = App.CH_TRACKING }
}
