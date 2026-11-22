package com.tony.nosotros.voice

/** A local serverTimestamp is null until acknowledged; it is never an expired packet. */
object VoicePacketExpiry {
    fun canClean(fromCache: Boolean, pending: Boolean, createdAt: Long?, now: Long): Boolean =
        !fromCache && !pending && createdAt != null && createdAt > 0 && now-createdAt>15_000
}
