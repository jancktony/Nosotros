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
enum class AudioFailure { CAPTURE, PLAYBACK }

class PushAudio(private val sendPacket: (ByteArray) -> Boolean, private val activity: () -> Unit = {},
                private val failure: (AudioFailure) -> Unit = {}, private val played: (Int) -> Unit = {}) {
    private val queue = ArrayBlockingQueue<ByteArray>(8)
    @Volatile private var alive = true
    @Volatile private var talking = false
    @Volatile private var input: AudioRecord? = null
    private var capture: Thread? = null
    private var sequence = 0
    private val stream = java.security.SecureRandom().nextInt()
    @Volatile private var playbackFailed = false
    val usable get() = alive && !playbackFailed
    private val output = PcmPlayback {
        val track=AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(VoiceWire.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(3200,AudioTrack.getMinBufferSize(VoiceWire.RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        try { check(track.state==AudioTrack.STATE_INITIALIZED);track.play() }
        catch(e:Exception){track.release();throw e}
        object:PcmDevice {
            override fun write(pcm:ByteBuffer)=track.write(pcm,pcm.remaining(),AudioTrack.WRITE_BLOCKING)
            override fun close(){runCatching{track.pause();track.flush()};track.release()}
        }
    }
    private val playback = thread(name = "walkie-play") {
        var codec: MediaCodec? = null
        try {
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
            codec.configure(VoiceCodec.format(), null, null, 0); codec.start()
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
                            if(info.size>0){output.write(pcm);played(info.size)}
                        }
                        codec.releaseOutputBuffer(buffer, false)
                        buffer = codec.dequeueOutputBuffer(info, 0)
                    }
                }
            }
        } catch (_: Exception) { if (alive) {playbackFailed=true;failure(AudioFailure.PLAYBACK)} }
        finally { runCatching { codec?.stop() }; codec?.release(); output.close() }
    }
    fun receive(buffer: DataChannel.Buffer) {
        if (!buffer.binary || buffer.data.remaining() !in 11..399) return
        receive(ByteArray(buffer.data.remaining()).also { buffer.data.get(it) })
    }
    fun receive(bytes: ByteArray) {
        if (!usable || VoiceWire.frames(bytes) == null) return
        activity()
        if (!queue.offer(bytes)) { queue.poll(); queue.offer(bytes) }
    }
    @SuppressLint("MissingPermission")
    @Synchronized fun start(onStopped: () -> Unit) {
        check(usable && !talking && capture?.isAlive != true)
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
            val framer=AmrFrames()
            val info = MediaCodec.BufferInfo(); var time = 0L
            fun flush() { if (frames.isNotEmpty()) {
                if (sendPacket(VoiceWire.packet(sequence++, frames.toList(), stream))) activity()
                frames.clear()
            } }
            fun drain(wait: Long) {
                var index = encoder.dequeueOutputBuffer(info, wait)
                while (index >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        encoder.getOutputBuffer(index)?.let { bytes ->
                            bytes.position(info.offset); bytes.limit(info.offset + info.size)
                            require(info.size<=8192)
                            val encoded=ByteArray(info.size).also {bytes.get(it)}
                            framer.feed(encoded).forEach {frame ->
                                frames.add(frame)
                                if(frames.size>=6)flush()
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(index, false)
                    index = encoder.dequeueOutputBuffer(info, 0)
                }
            }
            try {
                val bytes = ByteArray(320)
                var checkRecordingAt=0L
                while (alive && talking) {
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(now>=checkRecordingAt){
                        check(rec.activeRecordingConfiguration?.isClientSilenced != true){"Android silenció el micrófono"}
                        checkRecordingAt=now+1000
                    }
                    val size = rec.read(bytes, 0, bytes.size, AudioRecord.READ_BLOCKING)
                    if (size <= 0) {
                        check(!alive || !talking){"Android no entregó audio del micrófono"}
                        break
                    }
                    val index = encoder.dequeueInputBuffer(20_000)
                    if (index >= 0) {
                        encoder.getInputBuffer(index)!!.apply { clear(); put(bytes, 0, size) }
                        encoder.queueInputBuffer(index, 0, size, time, 0); time += size / 2 * 1_000_000L / VoiceWire.RATE
                    }
                    drain(0)
                }
                drain(20_000); flush()
            } catch (_: Exception) { if (alive && talking) failure(AudioFailure.CAPTURE) }
            finally {
                talking = false; runCatching { rec.stop() }; rec.release(); input = null
                runCatching { encoder.stop() }; encoder.release(); onStopped()
            }
        }
    }
    fun stop() { talking = false; runCatching { input?.stop() }; capture?.join(300) }
    fun close() {
        alive = false; stop(); queue.clear()
        output.close(); playback.join(300)
    }
}
