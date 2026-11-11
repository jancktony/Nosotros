package com.tony.nosotros.presence

import android.content.Context
import android.app.AppOpsManager
import android.os.Process
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.tony.nosotros.data.Repo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

object UsagePermission {
    fun granted(context: Context): Boolean = context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

data class Presence(val sharing: Boolean = false, val label: String = "", val state: String = "idle",
                    val since: Long = 0, val updatedAt: Long = 0) {
    fun fresh(now: Long, sleep: Boolean) = sharing && updatedAt > 0 && now - updatedAt in 0..(if (sleep) 12 * 60_000L else 3 * 60_000L)
    fun text(now: Long, sleep: Boolean): String? {
        if (!fresh(now, sleep)) return null
        if (!sleep) return label.takeIf { it.isNotBlank() }?.let { "Última app detectada: $it" }
        val minutes = ((now - since).coerceAtLeast(0) / 60_000).toInt()
        val duration = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
        return when (state) {
            "awake" -> "Despierto · teléfono en uso"
            "sleeping" -> "🌙 Posiblemente durmiendo · $duration"
            "overdue" -> "🌙 Pasó su hora habitual de despertar · $duration estimados"
            else -> null
        }
    }
    companion object {
        fun from(s: DocumentSnapshot?) = Presence(s?.getBoolean("sharing") == true,
            s?.getString("label").orEmpty(), s?.getString("state") ?: "idle", s?.getLong("since") ?: 0,
            s?.getTimestamp("updatedAt")?.toDate()?.time ?: 0)
    }
}

object PresenceRepo {
    private fun ref(pair: String, uid: String, sleep: Boolean) = Repo.db.collection("pairs").document(pair)
        .collection(if (sleep) "sleepPresence" else "appPresence").document(uid)
    fun flow(pair: String, uid: String, sleep: Boolean) = callbackFlow {
        val listener = ref(pair, uid, sleep).addSnapshotListener { data, error ->
            trySend(if (error == null && data?.metadata?.isFromCache == false) Presence.from(data) else Presence())
        }
        awaitClose { listener.remove() }
    }
    fun clear(pair: String, uid: String, sleep: Boolean) = ref(pair, uid, sleep)
        .set(mapOf("sharing" to false, "updatedAt" to FieldValue.serverTimestamp()))
    fun publish(pair: String, uid: String, sleep: Boolean, label: String = "", state: String = "idle", since: Long = 0) =
        ref(pair, uid, sleep).set(if (sleep) mapOf("sharing" to true, "state" to state,
            "since" to since, "updatedAt" to FieldValue.serverTimestamp()) else mapOf("sharing" to true,
            "label" to label.take(100), "updatedAt" to FieldValue.serverTimestamp()))
}
