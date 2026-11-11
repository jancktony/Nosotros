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
    private var pairListener: ListenerRegistration? = null
    private var callsListener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var partner: String? = null
    private var requested = false
    private var uid = ""
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val focus by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change -> if (change < 0) {
                stopTalking(); connection?.end("Audio interrumpido. El canal está en espera.")
            } }, handler).build()
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { App.instance.prefs.receiveWalkie = false; stopSelf(); return START_NOT_STICKY }
        if (!Repo.authorizedSession || !App.instance.prefs.receiveWalkie) { stopSelf(); return START_NOT_STICKY }
        if (connection != null) return START_NOT_STICKY
        uid = Repo.uid ?: run { stopSelf(); return START_NOT_STICKY }
        val pair = App.instance.prefs.pairId ?: run { stopSelf(); return START_NOT_STICKY }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Recepción de voz en segundo plano", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        WalkieHub.service = this
        WalkieHub.state.value = WalkieHub.State(enabled = true, message = "En espera · pulsa para hablar")
        try {
            connection = WalkieConnection(this, pair, uid) { message, ready ->
                if (!ready) { stopTalking(); audioManager.abandonAudioFocusRequest(focus) }
                WalkieHub.state.value = WalkieHub.state.value.copy(enabled = true, connected = ready, message = message)
                if (ready) {
                    if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        connection?.end("El audio está ocupado"); return@WalkieConnection
                    }
                    if (requested) startTalking()
                }
            }
        } catch (_: Exception) { WalkieHub.state.value = WalkieHub.State(message = "No se pudo abrir el walkie"); stopSelf(); return START_NOT_STICKY }
        authListener = FirebaseAuth.AuthStateListener { auth -> if (auth.currentUser?.uid != uid || !Repo.authorizedSession) stopSelf() }
            .also { FirebaseAuth.getInstance().addAuthStateListener(it) }
        pairListener = Repo.db.collection("pairs").document(pair).addSnapshotListener { snapshot, error ->
            val members = (snapshot?.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
            val modes = (snapshot?.get("featureFlags") as? Map<*, *>)?.entries?.mapNotNull { (k,v) -> if(k is String && v is String) k to v else null }?.toMap().orEmpty()
            if (error != null || uid !in members || !FeatureAccess(modes, snapshot?.getString("admin") == uid).enabled(Feature.WALKIE)) { stopSelf(); return@addSnapshotListener }
            partner = members.firstOrNull { it != uid }
            if (WalkieHub.foregroundOpen) prepare()
        }
        callsListener = Repo.db.collection("pairs").document(pair).collection("walkieCalls")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(10).addSnapshotListener { snapshot, error ->
                if (error != null) { connection?.end("No se pudo acceder al canal privado"); return@addSnapshotListener }
                val incoming = snapshot?.documents?.firstOrNull {
                    it.getString("to") == uid && it.getString("answer") == null &&
                        (it.getTimestamp("createdAt")?.toDate()?.time ?: 0) > System.currentTimeMillis() - 45000
                } ?: return@addSnapshotListener
                val current = connection ?: return@addSnapshotListener
                if (current.currentCallId == incoming.id) return@addSnapshotListener
                if (current.active) {
                    // Deterministic resolution when both press simultaneously.
                    if (incoming.getString("uid").orEmpty() >= uid) return@addSnapshotListener
                    current.end()
                }
                if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return@addSnapshotListener
                current.accept(incoming)
            }
        return START_NOT_STICKY
    }
    fun prepare() {
        val current = connection ?: return
        val to = partner ?: return
        if (!current.active) current.dial(to)
    }
    fun talk() {
        val current = connection ?: return
        requested = true
        if (WalkieHub.state.value.connected) startTalking()
        else if (!current.active) {
            val to = partner ?: run { requested = false; WalkieHub.state.value = WalkieHub.state.value.copy(message = "Cargando el canal… inténtalo en un momento"); return }
            current.dial(to)
            // The mic starts only if the finger is still pressed when the channel becomes ready.
            requested = true
        }
    }
    private fun startTalking() {
        if (!requested || WalkieHub.state.value.talking) return
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stopTalking(); return
        }
        WalkieHub.state.value = WalkieHub.state.value.copy(talking = true, message = "Hablando")
        connection?.talk { WalkieHub.state.value = WalkieHub.state.value.copy(talking = false) }
    }
    fun stopTalking() {
        requested = false
        connection?.stopTalking()
        WalkieHub.state.value = WalkieHub.state.value.copy(talking = false)
    }
    override fun onDestroy() {
        stopTalking(); pairListener?.remove(); callsListener?.remove()
        authListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
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
