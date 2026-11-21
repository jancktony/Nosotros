package com.tony.nosotros.data

import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class SharedJourney(val uid: String, val name: String, val lat: Double, val lng: Double,
    val etaAt: Long, val expiresAt: Long, val state: String)
object SharedJourneys {
    private fun root(pair: String) = Repo.db.collection("pairs").document(pair).collection("journeys")
    fun flow(pair: String) = callbackFlow<List<SharedJourney>> {
        val listener = root(pair).addSnapshotListener { s, _ -> trySend(s?.documents.orEmpty().mapNotNull { d ->
            val lat=d.getDouble("lat") ?: return@mapNotNull null;val lng=d.getDouble("lng") ?: return@mapNotNull null
            SharedJourney(d.id,d.getString("name").orEmpty(),lat,lng,d.getLong("etaAt") ?: 0,d.getLong("expiresAt") ?: 0,d.getString("state").orEmpty())
        }) }
        awaitClose { listener.remove() }
    }
    suspend fun share(pair: String, name: String, lat: Double, lng: Double, seconds: Double) {
        val uid=Repo.ensureSignedIn();val now=System.currentTimeMillis()
        val duration=(seconds.coerceIn(60.0,43_200.0)*1000).toLong()
        root(pair).document(uid).set(mapOf("name" to name.take(120),"lat" to lat,"lng" to lng,
            "etaAt" to now+duration,"expiresAt" to now+(duration*2).coerceIn(3_600_000,86_400_000),
            "state" to "active","updatedAt" to FieldValue.serverTimestamp())).await()
    }
    suspend fun cancel(pair: String) { root(pair).document(Repo.ensureSignedIn()).delete().await() }
    fun arrive(pair: String, uid: String) = root(pair).document(uid).update(mapOf("state" to "arrived","updatedAt" to FieldValue.serverTimestamp()))
}
