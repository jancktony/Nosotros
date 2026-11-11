package com.tony.nosotros.voice

import java.nio.ByteBuffer

/** Small bounded AMR-NB speech packets; never accept an unbounded peer payload. */
object VoiceWire {
    const val CHANNEL = "walkie-amrnb-v1"
    const val BITRATE = 7950
    const val RATE = 8000
    fun compatible(version: String): Boolean {
        val v = version.split('.').map { it.toIntOrNull() ?: 0 }
        return v.getOrElse(0) { 0 } > 1 || (v.getOrElse(0) { 0 } == 1 && v.getOrElse(1) { 0 } >= 10)
    }
    fun packet(sequence: Int, frames: List<ByteArray>, stream: Int = 0): ByteArray {
        require(frames.size in 1..6 && frames.all { it.size in 1..64 })
        val out = ByteBuffer.allocate(9 + frames.sumOf { it.size + 1 }).putInt(stream).putInt(sequence).put(frames.size.toByte())
        frames.forEach { out.put(it.size.toByte()); out.put(it) }
        return out.array()
    }
    fun frames(bytes: ByteArray): Triple<Int, Int, List<ByteArray>>? = runCatching {
        require(bytes.size in 11..399)
        val input = ByteBuffer.wrap(bytes); val stream = input.int; val seq = input.int; val count = input.get().toInt() and 255
        require(seq >= 0 && count in 1..6)
        val frames = List(count) {
            val size = input.get().toInt() and 255
            require(size in 1..64 && size <= input.remaining())
            ByteArray(size).also { input.get(it) }
        }
        require(!input.hasRemaining()); Triple(stream, seq, frames)
    }.getOrNull()
    fun bundle(packets: List<ByteArray>): ByteArray {
        require(packets.size in 1..10 && packets.all { frames(it) != null })
        val out = ByteBuffer.allocate(packets.sumOf { it.size + 2 })
        packets.forEach { out.putShort(it.size.toShort()); out.put(it) }
        return out.array().also { require(it.size <= 4096) }
    }
    fun unbundle(bytes: ByteArray): List<ByteArray>? = runCatching {
        require(bytes.size in 13..4096)
        val input = ByteBuffer.wrap(bytes); val result = mutableListOf<ByteArray>()
        while (input.hasRemaining()) {
            require(result.size < 10 && input.remaining() >= 2)
            val size = input.short.toInt() and 65535
            require(size in 11..399 && size <= input.remaining())
            val packet = ByteArray(size).also { input.get(it) }
            require(frames(packet) != null); result.add(packet)
        }
        result
    }.getOrNull()
}
