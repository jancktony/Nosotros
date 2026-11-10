package com.tony.nosotros.data

import com.google.firebase.firestore.DocumentSnapshot

/** Modo de desplazamiento. VEHICLE = vehículo aún sin clasificar. */
enum class Mode(val label: String) {
    STILL("Quieto"), WALK("A pie"), BIKE("En bici"), CAR("En carro"),
    MOTO("En moto"), BUS("En bus"), VEHICLE("En vehículo"), UNKNOWN("—");

    val moving get() = this != STILL && this != UNKNOWN
    val isVehicle get() = this == CAR || this == MOTO || this == BUS || this == VEHICLE

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: UNKNOWN
        val correctable = listOf(WALK, BIKE, CAR, MOTO, BUS)
    }
}

data class Member(
    val uid: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val acc: Float = 0f,
    val speedKmh: Float = 0f,
    val mode: Mode = Mode.UNKNOWN,
    val battery: Int = -1,
    val charging: Boolean = false,
    val updatedAt: Long = 0L,
    val pausedUntil: Long = 0L,
    val liveRequestedUntil: Long = 0L,
    val route: String = "",
    val placeName: String? = null,
    val saving: Boolean = false,
    val appVersion: String = "",
    val avatar: String = "",
) {
    val hasLocation get() = updatedAt > 0 && (lat != 0.0 || lng != 0.0)
    val paused get() = pausedUntil > System.currentTimeMillis()

    companion object {
        fun from(d: DocumentSnapshot) = Member(
            uid = d.id,
            name = d.getString("name") ?: "",
            lat = d.getDouble("lat") ?: 0.0,
            lng = d.getDouble("lng") ?: 0.0,
            acc = (d.getDouble("acc") ?: 0.0).toFloat(),
            speedKmh = (d.getDouble("speedKmh") ?: 0.0).toFloat(),
            mode = Mode.of(d.getString("mode")),
            battery = (d.getLong("battery") ?: -1L).toInt(),
            charging = d.getBoolean("charging") ?: false,
            updatedAt = d.getLong("updatedAt") ?: 0L,
            pausedUntil = d.getLong("pausedUntil") ?: 0L,
            liveRequestedUntil = d.getLong("liveRequestedUntil") ?: 0L,
            route = d.getString("route") ?: "",
            placeName = d.getString("placeName"),
            saving = d.getBoolean("saving") ?: false,
            appVersion = d.getString("appVersion") ?: "",
            avatar = d.getString("avatar") ?: "",
        )
    }
}

data class Place(
    val id: String = "",
    val owner: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radius: Double = 100.0,
    val visits: Long = 0,
    val suggested: Boolean = false,
) {
    companion object {
        fun from(d: DocumentSnapshot) = Place(
            id = d.id,
            owner = d.getString("owner") ?: "",
            name = d.getString("name") ?: "",
            lat = d.getDouble("lat") ?: 0.0,
            lng = d.getDouble("lng") ?: 0.0,
            radius = d.getDouble("radius") ?: 100.0,
            visits = d.getLong("visits") ?: 0,
            suggested = d.getBoolean("suggested") ?: false,
        )
    }
}

data class EventItem(
    val id: String, val uid: String, val type: String,
    val placeName: String?, val at: Long, val until: Long,
) {
    companion object {
        fun from(d: DocumentSnapshot) = EventItem(
            d.id, d.getString("uid") ?: "", d.getString("type") ?: "",
            d.getString("placeName"), d.getLong("at") ?: 0L, d.getLong("until") ?: 0L,
        )
    }
}

data class Trip(
    val id: String, val uid: String, val mode: Mode, val start: Long, val end: Long,
    val distanceM: Double, val from: String?, val to: String?, val path: String,
) {
    companion object {
        fun from(d: DocumentSnapshot) = Trip(
            d.id, d.getString("uid") ?: "", Mode.of(d.getString("mode")),
            d.getLong("start") ?: 0L, d.getLong("end") ?: 0L, d.getDouble("distanceM") ?: 0.0,
            d.getString("from"), d.getString("to"), d.getString("path") ?: "",
        )
    }
}

data class Visit(
    val id: String, val uid: String, val placeId: String, val placeName: String,
    val start: Long, val end: Long?,
) {
    companion object {
        fun from(d: DocumentSnapshot) = Visit(
            d.id, d.getString("uid") ?: "", d.getString("placeId") ?: "",
            d.getString("placeName") ?: "", d.getLong("start") ?: 0L, d.getLong("end"),
        )
    }
}

/** Datos compartidos de la pareja: quién administra y qué versión está liberada. */
data class PairInfo(
    val admin: String = "", val relVersion: String? = null, val relUrl: String = "", val relNotes: String = "",
    val featureFlags: Map<String, String> = emptyMap(),
    val testVersion: String? = null, val creatorBrand: String = "jancktony",
)

data class Photo(
    val id: String, val uid: String, val at: Long, val caption: String,
    val place: String?, val lat: Double, val lng: Double, val thumb: ByteArray,
) {
    companion object {
        fun from(d: DocumentSnapshot) = Photo(
            d.id, d.getString("uid") ?: "", d.getLong("at") ?: 0L, d.getString("caption") ?: "",
            d.getString("place"), d.getDouble("lat") ?: 0.0, d.getDouble("lng") ?: 0.0,
            d.getBlob("thumb")?.toBytes() ?: ByteArray(0),
        )
    }
}

data class ReleaseReview(val state: String = "pending", val notes: String = "")
