package com.tony.nosotros.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceWireTest {
    @Test fun compressedSpeechSurvivesPacketAndRelayFraming() {
        val frames = List(6) { n -> ByteArray(21) { (it+n).toByte() } }
        val packet = VoiceWire.packet(12, frames, 153)
        val parsed = VoiceWire.frames(packet)!!
        assertEquals(153, parsed.first); assertEquals(12, parsed.second)
        frames.zip(parsed.third).forEach { (a,b) -> assertArrayEquals(a,b) }
        val relay = VoiceWire.unbundle(VoiceWire.bundle(listOf(packet,packet)))!!
        assertEquals(2,relay.size); assertArrayEquals(packet,relay[0])
    }
    @Test fun malformedAndOversizedPacketsAreRejected() {
        assertNull(VoiceWire.frames(ByteArray(400)))
        assertNull(VoiceWire.frames(byteArrayOf(1,2)))
        val packet = VoiceWire.packet(0,listOf(ByteArray(21)))
        assertNull(VoiceWire.frames(packet.copyOf(packet.size-1)))
        assertNull(VoiceWire.frames(packet+byteArrayOf(1)))
        assertNull(VoiceWire.unbundle(byteArrayOf(127,127)))
        assertNull(VoiceWire.unbundle(ByteArray(4097)))
    }
    @Test fun newProtocolDoesNotSendCompressedSpeechToLegacyPcmPhones() {
        assertFalse(VoiceWire.compatible("1.9.3")); assertFalse(VoiceWire.compatible(""))
        assertTrue(VoiceWire.compatible("1.10.0")); assertTrue(VoiceWire.compatible("2.0"))
    }
}
