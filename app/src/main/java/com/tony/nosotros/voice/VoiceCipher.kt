package com.tony.nosotros.voice

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The relay receives opaque authenticated packets, not the pair's audio key. */
internal class VoiceCipher(key: ByteArray, private val room: String) {
    private val secret = SecretKeySpec(key.copyOf(), "AES")
    private val random = SecureRandom()
    init { require(key.size == 32); require(room.matches(Regex("[a-f0-9]{64}"))) }
    fun seal(packet: ByteArray, role: String, now: Long): ByteArray {
        require(packet.size in 11..399 && role in listOf("owner", "partner"))
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
        cipher.updateAAD("NosotrosVoice1|$room|$role".toByteArray(Charsets.UTF_8))
        val body = ByteBuffer.allocate(8 + packet.size).putLong(now).put(packet).array()
        return byteArrayOf(1) + iv + cipher.doFinal(body)
    }
    fun open(envelope: ByteArray, expectedRole: String, now: Long): ByteArray? = runCatching {
        require(envelope.size in 48..436 && envelope[0] == 1.toByte())
        require(expectedRole in listOf("owner", "partner"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD("NosotrosVoice1|$room|$expectedRole".toByteArray(Charsets.UTF_8))
        val body = ByteBuffer.wrap(cipher.doFinal(envelope.copyOfRange(13, envelope.size)))
        val at = body.long
        require(now - at in -2000L..6000L)
        ByteArray(body.remaining()).also(body::get).also { require(it.size in 11..399) }
    }.getOrNull()
}
