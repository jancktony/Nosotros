package com.tony.nosotros.find

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.*
import com.tony.nosotros.data.Feature
import com.tony.nosotros.data.Prefs
import com.tony.nosotros.data.Repo
import kotlinx.coroutines.flow.MutableStateFlow

/** Opt-in sound on this phone. No new status-bar notifications, volume override or lock-screen bypass. */
object PhoneFinder {
    val ringing = MutableStateFlow(false)
    private val handler = Handler(Looper.getMainLooper())
    private var tone: Ringtone? = null
    private var command: DocumentReference? = null
    private var listener: ListenerRegistration? = null
    private val timeout = Runnable { stop() }
    fun listen(context: Context, prefs: Prefs, pair: String, uid: String) {
        close()
        Repo.updateMe(pair, mapOf("findPhoneEnabled" to prefs.allowFindPhone))
        listener = Repo.db.collection("pairs").document(pair).collection("deviceCommands").document(uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
                if (error != null || doc == null || doc.metadata.isFromCache || doc.metadata.hasPendingWrites()) return@addSnapshotListener
                val id = doc.getString("requestId").orEmpty()
                if (doc.getString("status") != "queued" || id.isBlank() || id == prefs.lastRingRequest) return@addSnapshotListener
                val at = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0
                val now = System.currentTimeMillis()
                if (now - at !in 0..60_000 || (doc.getLong("expiresAt") ?: 0) <= now) return@addSnapshotListener
                prefs.lastRingRequest = id
                if (!prefs.allowFindPhone || !prefs.featureEnabled(Feature.FIND_PHONE)) {
                    doc.reference.update(mapOf("status" to "unavailable", "handledAt" to FieldValue.serverTimestamp()))
                    return@addSnapshotListener
                }
                stop()
                command = doc.reference
                try {
                    val sound = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                        ?: error("No alarm sound")
                    sound.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    sound.isLooping = true; tone = sound; sound.play()
                    ringing.value = true
                    doc.reference.update(mapOf("status" to "ringing", "handledAt" to FieldValue.serverTimestamp()))
                    handler.postDelayed(timeout, 30_000)
                } catch (_: Exception) {
                    doc.reference.update(mapOf("status" to "unavailable", "handledAt" to FieldValue.serverTimestamp()))
                    command = null
                }
            }
    }
    fun stop() {
        handler.removeCallbacks(timeout); runCatching { tone?.stop() }; tone = null; ringing.value = false
        command?.let { if (Repo.authorizedSession) it.update(mapOf("status" to "stopped", "handledAt" to FieldValue.serverTimestamp())) }
        command = null
    }
    fun close() { listener?.remove(); listener = null; stop() }
}
