package com.tony.nosotros.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceCipherTest {
    private val key = ByteArray(32) { it.toByte() }
    private val room = "a".repeat(64)
    private val packet = ByteArray(35) { (it + 2).toByte() }
    @Test fun peersDecryptOnlyWithMatchingRoomKeyAndRole() {
        val sender = VoiceCipher(key, room)
        val receiver = VoiceCipher(key, room)
        val wire = sender.seal(packet, "owner", 10000)
        assertArrayEquals(packet, receiver.open(wire, "owner", 10500))
        assertNull(receiver.open(wire, "partner", 10500))
        assertNull(VoiceCipher(ByteArray(32) { 7 }, room).open(wire, "owner", 10500))
        assertNull(VoiceCipher(key, "b".repeat(64)).open(wire, "owner", 10500))
        assertFalse(wire.toList().windowed(packet.size).any { it == packet.toList() })
    }
    @Test fun tamperingAndStaleOrFutureSpeechAreRejected() {
        val crypto = VoiceCipher(key, room)
        val wire = crypto.seal(packet, "partner", 10000)
        val changed = wire.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertNull(crypto.open(changed, "partner", 10500))
        assertNull(crypto.open(wire, "partner", 17001))
        assertNull(crypto.open(wire, "partner", 7000))
        assertNull(crypto.open(ByteArray(10000), "partner", 10000))
        assertNull(crypto.open(byteArrayOf(1), "partner", 10000))
    }
    @Test fun freshPacketsUseDifferentNoncesAndVoicePayloadHasBoundedSize() {
        val crypto = VoiceCipher(key, room)
        val one = crypto.seal(packet, "owner", 10000)
        val two = crypto.seal(packet, "owner", 10000)
        assertFalse(one.contentEquals(two))
        assertArrayEquals(packet, crypto.open(one, "owner", 10000))
        assertArrayEquals(packet, crypto.open(two, "owner", 10000))
        assertEquals(436, crypto.seal(ByteArray(399), "owner", 10000).size)
    }
}
