package com.tony.nosotros.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.tony.nosotros.util.TimeFmt
import kotlin.random.Random

/**
 * Todo el acceso a Firebase.
 *
 * Estructura:
 *  codes/{código}                  -> { pairId, owner, expiresAt }
 *  pairs/{pairId}                  -> { members:[uid,uid], code }
 *  pairs/{pairId}/members/{uid}    -> posición y estado en vivo
 *  pairs/{pairId}/places/{id}      -> lugares (y sugerencias)
 *  pairs/{pairId}/events/{id}      -> llegó / salió / pausa / reanudó
 *  pairs/{pairId}/trips/{id}       -> trayectos con ruta
 *  pairs/{pairId}/visits/{id}      -> estancias en lugares
 */
object Repo {
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user!!.uid
    }

    private fun pair(pairId: String) = db.collection("pairs").document(pairId)

    // ---------- Vinculación ----------

    /** Crea la pareja y devuelve un código de 6 dígitos para compartir. */
    suspend fun createPair(): Pair<String, String> {
        val me = ensureSignedIn()
        val code = Random.nextInt(100000, 999999).toString()
        val ref = db.collection("pairs").document()
        ref.set(mapOf("members" to listOf(me), "code" to code, "admin" to me, "createdAt" to System.currentTimeMillis())).await()
        db.collection("codes").document(code).set(
            mapOf("pairId" to ref.id, "owner" to me, "expiresAt" to System.currentTimeMillis() + 24 * 3600_000L)
        ).await()
        return ref.id to code
    }

    /** Une este teléfono a la pareja del código. Devuelve el pairId. */
    suspend fun joinWithCode(code: String): String {
        val me = ensureSignedIn()
        val c = db.collection("codes").document(code.trim()).get().await()
        if (!c.exists()) error("Código no encontrado")
        if ((c.getLong("expiresAt") ?: 0L) < System.currentTimeMillis()) error("El código expiró, genera uno nuevo")
        val pairId = c.getString("pairId")!!
        pair(pairId).update(mapOf("members" to FieldValue.arrayUnion(me), "joinCode" to code.trim())).await()
        return pairId
    }

    fun pairMembersFlow(pairId: String): Flow<List<String>> = callbackFlow {
        val reg = pair(pairId).addSnapshotListener { s, _ ->
            @Suppress("UNCHECKED_CAST")
            trySend((s?.get("members") as? List<String>) ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    /** Fecha de inicio de la relación (compartida por los dos). */
    fun sinceFlow(pairId: String): Flow<Long> = callbackFlow {
        val reg = pair(pairId).addSnapshotListener { s, _ -> trySend(s?.getLong("since") ?: 0L) }
        awaitClose { reg.remove() }
    }

    fun setSince(pairId: String, millis: Long) {
        pair(pairId).update("since", millis)
    }

    private fun info(s: com.google.firebase.firestore.DocumentSnapshot?): PairInfo {
        @Suppress("UNCHECKED_CAST")
        val members = (s?.get("members") as? List<String>) ?: emptyList()
        return PairInfo(
            admin = s?.getString("admin") ?: members.firstOrNull() ?: "",
            relVersion = s?.getString("relVersion"), relUrl = s?.getString("relUrl") ?: "", relNotes = s?.getString("relNotes") ?: "",
        )
    }

    fun pairInfoFlow(pairId: String): Flow<PairInfo> = callbackFlow {
        val reg = pair(pairId).addSnapshotListener { s, _ -> trySend(info(s)) }
        awaitClose { reg.remove() }
    }

    suspend fun pairInfo(pairId: String): PairInfo = info(pair(pairId).get().await())

    /** El administrador elige qué versión recibe la pareja. */
    fun setRelease(pairId: String, version: String, url: String, notes: String) {
        pair(pairId).update(mapOf("relVersion" to version, "relUrl" to url, "relNotes" to notes))
    }

    fun setAdmin(pairId: String, uid: String) { pair(pairId).update("admin", uid) }

    /** Saca al otro teléfono y crea un código nuevo para volver a vincular. */
    suspend fun resetPartner(pairId: String, partnerUid: String?): String {
        val me = ensureSignedIn()
        val code = Random.nextInt(100000, 999999).toString()
        val changes = HashMap<String, Any>(mapOf("code" to code, "admin" to me))
        if (partnerUid != null) changes["members"] = FieldValue.arrayRemove(partnerUid)
        pair(pairId).update(changes).await()
        db.collection("codes").document(code).set(
            mapOf("pairId" to pairId, "owner" to me, "expiresAt" to System.currentTimeMillis() + 24 * 3600_000L)
        ).await()
        return code
    }

    /** Da acceso al navegador que muestra este código en el panel web. */
    suspend fun linkWeb(pairId: String, code: String) {
        val ref = db.collection("weblinks").document(code.trim().uppercase())
        val webUid = ref.get().await().getString("uid") ?: error("Código no encontrado")
        pair(pairId).update("webUids", FieldValue.arrayUnion(webUid)).await()
        ref.update("pairId", pairId).await()
    }

    fun unlinkWeb(pairId: String) { pair(pairId).update("webUids", FieldValue.delete()) }

    // ---------- Fotos ----------

    suspend fun addPhoto(pairId: String, thumb: ByteArray, full: ByteArray, caption: String, place: String?, lat: Double, lng: Double) {
        val me = ensureSignedIn()
        val ref = pair(pairId).collection("photos").document()
        ref.collection("full").document("img").set(mapOf("img" to com.google.firebase.firestore.Blob.fromBytes(full))).await()
        ref.set(mapOf("uid" to me, "at" to System.currentTimeMillis(), "caption" to caption, "place" to place,
            "lat" to lat, "lng" to lng, "thumb" to com.google.firebase.firestore.Blob.fromBytes(thumb))).await()
        addEvent(pairId, "photo", caption.ifBlank { null })
    }

    fun photosFlow(pairId: String): Flow<List<Photo>> = callbackFlow {
        val reg = pair(pairId).collection("photos").orderBy("at", Query.Direction.DESCENDING).limit(40)
            .addSnapshotListener { s, _ -> trySend(s?.documents?.map { Photo.from(it) } ?: emptyList()) }
        awaitClose { reg.remove() }
    }

    suspend fun fullPhoto(pairId: String, id: String): ByteArray? = runCatching {
        pair(pairId).collection("photos").document(id).collection("full").document("img").get().await().getBlob("img")?.toBytes()
    }.getOrNull()

    fun deletePhoto(pairId: String, id: String) {
        val ref = pair(pairId).collection("photos").document(id)
        ref.collection("full").document("img").delete(); ref.delete()
    }

    suspend fun cleanupCode(pairId: String) {
        runCatching {
            val code = pair(pairId).get().await().getString("code") ?: return
            db.collection("codes").document(code).delete().await()
        }
    }

    suspend fun unlink(pairId: String) {
        val me = uid ?: return
        runCatching { pair(pairId).update("members", FieldValue.arrayRemove(me)).await() }
    }

    // ---------- Miembros ----------

    fun updateMe(pairId: String, fields: Map<String, Any?>) {
        val me = uid ?: return
        pair(pairId).collection("members").document(me).set(fields, SetOptions.merge())
    }

    fun requestLive(pairId: String, partnerUid: String, minutes: Int = 5) {
        pair(pairId).collection("members").document(partnerUid)
            .set(mapOf("liveRequestedUntil" to System.currentTimeMillis() + minutes * 60_000L), SetOptions.merge())
    }

    fun memberFlow(pairId: String, memberUid: String): Flow<Member?> = callbackFlow {
        val reg = pair(pairId).collection("members").document(memberUid).addSnapshotListener { s, _ ->
            trySend(if (s != null && s.exists()) Member.from(s) else null)
        }
        awaitClose { reg.remove() }
    }

    // ---------- Lugares ----------

    fun placesFlow(pairId: String): Flow<List<Place>> = callbackFlow {
        val reg = pair(pairId).collection("places").addSnapshotListener { s, _ ->
            trySend(s?.documents?.map { Place.from(it) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun savePlace(pairId: String, place: Place) {
        val col = pair(pairId).collection("places")
        val ref = if (place.id.isEmpty()) col.document() else col.document(place.id)
        ref.set(
            mapOf(
                "owner" to place.owner, "name" to place.name, "lat" to place.lat, "lng" to place.lng,
                "radius" to place.radius, "visits" to place.visits, "suggested" to place.suggested,
            ), SetOptions.merge()
        )
    }

    fun deletePlace(pairId: String, id: String) {
        pair(pairId).collection("places").document(id).delete()
    }

    fun incrementVisits(pairId: String, placeId: String) {
        pair(pairId).collection("places").document(placeId).update("visits", FieldValue.increment(1))
    }

    // ---------- Eventos ----------

    fun addEvent(pairId: String, type: String, placeName: String? = null, until: Long = 0L) {
        val me = uid ?: return
        pair(pairId).collection("events").add(
            mapOf("uid" to me, "type" to type, "placeName" to placeName,
                "at" to System.currentTimeMillis(), "until" to until)
        )
    }

    fun eventsSinceFlow(pairId: String, since: Long): Flow<List<EventItem>> = callbackFlow {
        val reg = pair(pairId).collection("events").whereGreaterThan("at", since)
            .orderBy("at", Query.Direction.ASCENDING)
            .addSnapshotListener { s, _ -> trySend(s?.documents?.map { EventItem.from(it) } ?: emptyList()) }
        awaitClose { reg.remove() }
    }

    // ---------- Trayectos y visitas ----------

    fun saveTrip(pairId: String, data: Map<String, Any?>) {
        pair(pairId).collection("trips").add(data)
    }

    fun setTripMode(pairId: String, tripId: String, mode: Mode) {
        pair(pairId).collection("trips").document(tripId).update("mode", mode.name)
    }

    fun startVisit(pairId: String, placeId: String, placeName: String): String {
        val me = uid ?: return ""
        val ref = pair(pairId).collection("visits").document()
        val now = System.currentTimeMillis()
        ref.set(mapOf("uid" to me, "placeId" to placeId, "placeName" to placeName,
            "start" to now, "end" to null, "day" to TimeFmt.dayKey(now)))
        return ref.id
    }

    fun endVisit(pairId: String, visitId: String) {
        pair(pairId).collection("visits").document(visitId).update("end", System.currentTimeMillis())
    }

    fun tripsFlow(pairId: String, day: String): Flow<List<Trip>> = callbackFlow {
        val reg = pair(pairId).collection("trips").whereEqualTo("day", day).addSnapshotListener { s, _ ->
            trySend(s?.documents?.map { Trip.from(it) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    fun visitsFlow(pairId: String, day: String): Flow<List<Visit>> = callbackFlow {
        val reg = pair(pairId).collection("visits").whereEqualTo("day", day).addSnapshotListener { s, _ ->
            trySend(s?.documents?.map { Visit.from(it) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }
}
