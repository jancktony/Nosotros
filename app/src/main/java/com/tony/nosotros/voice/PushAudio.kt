package com.tony.nosotros.voice

import android.annotation.SuppressLint
import android.media.*
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** PCM over encrypted WebRTC data channel. AudioRecord exists only while pressed. */
class PushAudio(private val channel: DataChannel, private val activity: () -> Unit = {}) {
    private val queue = ArrayBlockingQueue<ByteArray>(10)
    @Volatile private var alive = true
    @Volatile private var talking = false
    @Volatile private var input: AudioRecord? = null
    private var capture: Thread? = null
    private val output = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setBufferSizeInBytes(maxOf(6400, AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
        .setTransferMode(AudioTrack.MODE_STREAM).build()
    private val playback = thread(name = "walkie-play") {
        try {
            output.play()
            while (alive) queue.poll(100, TimeUnit.MILLISECONDS)?.let { output.write(it, 0, it.size) }
        } catch (_: Exception) { /* Closing releases the audio device. */ }
    }
    fun receive(buffer: DataChannel.Buffer) {
        if (!alive || !buffer.binary || buffer.data.remaining() !in 2..1280 || buffer.data.remaining() % 2 != 0) return
        activity()
        val bytes = ByteArray(buffer.data.remaining()); buffer.data.get(bytes)
        if (!queue.offer(bytes)) { queue.poll(); queue.offer(bytes) }
    }
    @SuppressLint("MissingPermission")
    @Synchronized fun start(onStopped: () -> Unit) {
        if (!alive || talking || capture?.isAlive == true) return
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(6400, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)))
        try { check(rec.state == AudioRecord.STATE_INITIALIZED); rec.startRecording() }
        catch (e: Exception) { rec.release(); throw e }
        input = rec; talking = true
        capture = thread(name = "walkie-record") {
            val started = android.os.SystemClock.elapsedRealtime()
            try {
                val bytes = ByteArray(640)
                while (alive && talking && android.os.SystemClock.elapsedRealtime() - started < 30000) {
                    val size = rec.read(bytes, 0, bytes.size)
                    if (size <= 0) break
                    if (talking && channel.state() == DataChannel.State.OPEN && channel.bufferedAmount() < 16000)
                        { channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes.copyOf(size)), true)); activity() }
                }
            } finally {
                talking = false
                runCatching { rec.stop() }; rec.release(); input = null
                onStopped()
            }
        }
    }
    fun stop() { talking = false; runCatching { input?.stop() }; capture?.join(300) }
    fun close() {
        alive = false; stop(); queue.clear()
        runCatching { output.pause(); output.flush() }
        playback.join(300); output.release()
    }
}
