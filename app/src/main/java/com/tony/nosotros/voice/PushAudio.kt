package com.tony.nosotros.voice

import android.annotation.SuppressLint
import android.media.*
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object VoiceCodec {
    fun format() = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AMR_NB, VoiceWire.RATE, 1)
        .apply { setInteger(MediaFormat.KEY_BIT_RATE, VoiceWire.BITRATE); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 320) }
}

/** AMR-NB speech at 7.95 kbit/s (~1 KB/s payload), with short bounded playback queues.
 * The recorder and encoder exist only while the visible activity's button is held. */
class PushAudio(private val sendPacket: (ByteArray) -> Boolean, private val activity: () -> Unit = {},
                private val failure: () -> Unit = {}) {
    private val queue = ArrayBlockingQueue<ByteArray>(8)
    @Volatile private var alive = true
    @Volatile private var talking = false
    @Volatile private var input: AudioRecord? = null
    private var capture: Thread? = null
    private var sequence = 0
    private val stream = java.security.SecureRandom().nextInt()
    private val output = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(VoiceWire.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setBufferSizeInBytes(maxOf(1600, AudioTrack.getMinBufferSize(VoiceWire.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
        .setTransferMode(AudioTrack.MODE_STREAM).build()
    private val playback = thread(name = "walkie-play") {
        var codec: MediaCodec? = null
        try {
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
            codec.configure(VoiceCodec.format(), null, null, 0); codec.start(); output.play()
            val info = MediaCodec.BufferInfo()
            var time = 0L; var lastSequence = -1; var currentStream: Int? = null
            val previousStreams = LinkedHashSet<Int>()
            while (alive) {
                val packet = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val decoded = VoiceWire.frames(packet) ?: continue
                if (decoded.first != currentStream) {
                    if (decoded.first in previousStreams) continue
                    currentStream?.let { previousStreams.add(it) }
                    while (previousStreams.size > 16) previousStreams.remove(previousStreams.first())
                    currentStream = decoded.first; lastSequence = -1
                }
                if (decoded.second <= lastSequence) continue
                lastSequence = decoded.second
                decoded.third.forEach { frame ->
                    val index = codec.dequeueInputBuffer(20_000)
                    if (index >= 0) {
                        codec.getInputBuffer(index)!!.apply { clear(); put(frame) }
                        codec.queueInputBuffer(index, 0, frame.size, time, 0); time += 20_000
                    }
                    var buffer = codec.dequeueOutputBuffer(info, 10_000)
                    while (buffer >= 0) {
                        codec.getOutputBuffer(buffer)?.let { pcm ->
                            pcm.position(info.offset); pcm.limit(info.offset + info.size)
                            output.write(pcm, info.size, AudioTrack.WRITE_BLOCKING)
                        }
                        codec.releaseOutputBuffer(buffer, false)
                        buffer = codec.dequeueOutputBuffer(info, 0)
                    }
                }
            }
        } catch (_: Exception) { if (alive) failure() }
        finally { runCatching { codec?.stop() }; codec?.release() }
    }
    fun receive(buffer: DataChannel.Buffer) {
        if (!buffer.binary || buffer.data.remaining() !in 11..399) return
        receive(ByteArray(buffer.data.remaining()).also { buffer.data.get(it) })
    }
    fun receive(bytes: ByteArray) {
        if (!alive || VoiceWire.frames(bytes) == null) return
        activity()
        if (!queue.offer(bytes)) { queue.poll(); queue.offer(bytes) }
    }
    @SuppressLint("MissingPermission")
    @Synchronized fun start(onStopped: () -> Unit) {
        check(alive && !talking && capture?.isAlive != true)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, VoiceWire.RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(3200, AudioRecord.getMinBufferSize(VoiceWire.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)))
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
        try {
            encoder.configure(VoiceCodec.format(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start()
            check(rec.state == AudioRecord.STATE_INITIALIZED); rec.startRecording()
        } catch (e: Exception) { rec.release(); encoder.release(); throw e }
        input = rec; talking = true
        capture = thread(name = "walkie-record") {
            val frames = mutableListOf<ByteArray>()
            val info = MediaCodec.BufferInfo(); var time = 0L
            fun flush() { if (frames.isNotEmpty()) {
                if (sendPacket(VoiceWire.packet(sequence++, frames.toList(), stream))) activity()
                frames.clear()
            } }
            fun drain(wait: Long) {
                var index = encoder.dequeueOutputBuffer(info, wait)
                while (index >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size in 1..64) {
                        encoder.getOutputBuffer(index)?.let { bytes ->
                            bytes.position(info.offset); bytes.limit(info.offset + info.size)
                            frames.add(ByteArray(info.size).also { bytes.get(it) })
                            if (frames.size >= 6) flush()
                        }
                    }
                    encoder.releaseOutputBuffer(index, false)
                    index = encoder.dequeueOutputBuffer(info, 0)
                }
            }
            try {
                val bytes = ByteArray(320)
                while (alive && talking) {
                    val size = rec.read(bytes, 0, bytes.size, AudioRecord.READ_BLOCKING)
                    if (size <= 0) break
                    val index = encoder.dequeueInputBuffer(20_000)
                    if (index >= 0) {
                        encoder.getInputBuffer(index)!!.apply { clear(); put(bytes, 0, size) }
                        encoder.queueInputBuffer(index, 0, size, time, 0); time += size / 2 * 1_000_000L / VoiceWire.RATE
                    }
                    drain(0)
                }
                drain(20_000); flush()
            } catch (_: Exception) { if (alive && talking) failure() }
            finally {
                talking = false; runCatching { rec.stop() }; rec.release(); input = null
                runCatching { encoder.stop() }; encoder.release(); onStopped()
            }
        }
    }
    fun stop() { talking = false; runCatching { input?.stop() }; capture?.join(300) }
    fun close() {
        alive = false; stop(); queue.clear()
        runCatching { output.pause(); output.flush() }; playback.join(300); output.release()
    }
}
