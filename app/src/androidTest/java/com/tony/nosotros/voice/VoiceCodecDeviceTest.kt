package com.tony.nosotros.voice

import android.media.MediaCodec
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Real Android codec loopback. Uses synthetic speech-band sound, no microphone or account. */
@RunWith(AndroidJUnit4::class)
class VoiceCodecDeviceTest {
    @Test fun lowBandwidthSpeechEncodesAndDecodesToAudibleSamples() {
        val frames = toneFrames(440.0)
        val info = MediaCodec.BufferInfo()
        assertTrue("No compressed audio", frames.size >= 80)
        assertTrue("Speech is not low bitrate", frames.sumOf { it.size } < 5000)
        assertTrue("Unsupported AMR frame layout", frames.all { it.size in 1..64 })
        val transported = frames.chunked(6).flatMapIndexed { seq, group ->
            VoiceWire.frames(VoiceWire.packet(seq, group, 17))!!.third
        }
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
        var samples = 0; var energy = 0.0
        try {
            decoder.configure(VoiceCodec.format(),null,null,0); decoder.start()
            fun drain(wait: Long) {
                var index = decoder.dequeueOutputBuffer(info,wait)
                while (index >= 0) {
                    decoder.getOutputBuffer(index)?.let { out ->
                        out.position(info.offset); out.limit(info.offset + info.size)
                        val pcm = out.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (pcm.hasRemaining()) { val value = pcm.get().toDouble(); energy += value * value; samples++ }
                    }
                    decoder.releaseOutputBuffer(index,false); index = decoder.dequeueOutputBuffer(info,0)
                }
            }
            transported.forEachIndexed { n, frame ->
                val index = decoder.dequeueInputBuffer(1_000_000); assertTrue(index >= 0)
                decoder.getInputBuffer(index)!!.apply { clear(); put(frame) }
                decoder.queueInputBuffer(index,0,frame.size,n * 20_000L,0); drain(1000)
            }
            repeat(10) { drain(20_000) }
        } finally { runCatching { decoder.stop() }; decoder.release() }
        assertTrue("Too few decoded samples", samples > 12000)
        assertTrue("Decoded audio is silent", energy / samples > 10000)
    }
    private fun toneFrames(frequency: Double): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        val info = MediaCodec.BufferInfo()
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
        try {
            encoder.configure(VoiceCodec.format(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start()
            fun drain(wait: Long): Boolean {
                var i = encoder.dequeueOutputBuffer(info, wait)
                var eos = false
                while (i >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val out = encoder.getOutputBuffer(i)!!
                        out.position(info.offset); out.limit(info.offset + info.size)
                        frames.add(ByteArray(info.size).also { out.get(it) })
                    }
                    eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(i, false)
                    i = encoder.dequeueOutputBuffer(info, 0)
                }
                return eos
            }
            repeat(100) { frame ->
                val index = encoder.dequeueInputBuffer(1_000_000); assertTrue(index >= 0)
                val pcm = ByteBuffer.allocate(320).order(ByteOrder.LITTLE_ENDIAN)
                repeat(160) { sample -> pcm.putShort((sin(2 * Math.PI * frequency * (frame * 160 + sample) / VoiceWire.RATE) * 10000).toInt().toShort()) }
                encoder.getInputBuffer(index)!!.apply { clear(); put(pcm.array()) }
                encoder.queueInputBuffer(index,0,320,frame * 20_000L,0); drain(1000)
            }
            val index = encoder.dequeueInputBuffer(1_000_000); assertTrue(index >= 0)
            encoder.queueInputBuffer(index,0,0,2_000_000,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            var done = false
            repeat(50) { if (!done) done = drain(20_000) }
            assertTrue("Encoder did not finish", done)
        } finally { runCatching { encoder.stop() }; encoder.release() }
        return frames
    }

    @Test fun twoRealPlaybackEnginesPlayBothDirectionsAndSubsequentTurns() {
        val frames=toneFrames(660.0).take(36)
        val bytesA=java.util.concurrent.atomic.AtomicInteger(0)
        val bytesB=java.util.concurrent.atomic.AtomicInteger(0)
        val errors=java.util.concurrent.CopyOnWriteArrayList<AudioFailure>()
        val a=PushAudio({true},failure={errors.add(it)},played={bytesA.addAndGet(it)})
        val b=PushAudio({true},failure={errors.add(it)},played={bytesB.addAndGet(it)})
        try {
            repeat(2){turn->
                val beforeA=bytesA.get();val beforeB=bytesB.get()
                frames.chunked(6).forEachIndexed { seq, group ->
                    b.receive(VoiceWire.packet(turn*6+seq,group,101));Thread.sleep(120)
                }
                frames.chunked(6).forEachIndexed { seq, group ->
                    a.receive(VoiceWire.packet(turn*6+seq,group,202));Thread.sleep(120)
                }
                val until=android.os.SystemClock.elapsedRealtime()+5000
                while((bytesA.get()-beforeA<3200||bytesB.get()-beforeB<3200)&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(20)
                assertTrue("Phone A did not play the peer's decoded voice",bytesA.get()-beforeA>=3200)
                assertTrue("Phone B did not play the peer's decoded voice",bytesB.get()-beforeB>=3200)
                assertTrue("Playback failed: $errors",errors.isEmpty())
            }
        } finally {a.close();b.close()}
    }

}
