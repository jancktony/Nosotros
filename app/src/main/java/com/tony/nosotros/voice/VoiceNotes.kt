package com.tony.nosotros.voice

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tony.nosotros.data.Repo
import java.io.File
import kotlinx.coroutines.tasks.await

class VoiceNotes(private val context: Context, private val pairId: String) {
    data class Note(val id: String, val uid: String, val duration: Long)
    private val notes get() = Repo.db.collection("pairs").document(pairId).collection("voiceNotes")
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L
    private var player: MediaPlayer? = null
    private var playingFile: File? = null

    fun listen(result: (List<Note>) -> Unit, error: () -> Unit): ListenerRegistration =
        notes.orderBy("createdAt", Query.Direction.DESCENDING).limit(30).addSnapshotListener { snapshot, e ->
            if (e != null) error() else result(snapshot?.documents.orEmpty().map {
                Note(it.id, it.getString("uid").orEmpty(), it.getLong("durationMs") ?: 0)
            })
        }

    @Suppress("DEPRECATION")
    fun start(onLimit: () -> Unit) {
        check(recorder == null)
        stopPlayback()
        val target = File.createTempFile("voice-", ".m4a", context.cacheDir)
        file = target
        val rec = MediaRecorder()
        recorder = rec
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)
            rec.setAudioSamplingRate(24000)
            rec.setAudioEncodingBitRate(24000)
            rec.setMaxDuration(30000)
            rec.setMaxFileSize(140000)
            rec.setOutputFile(target.absolutePath)
            rec.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) onLimit()
            }
            rec.prepare(); rec.start()
            started = android.os.SystemClock.elapsedRealtime()
        } catch (e: Exception) { cancel(); throw e }
    }

    suspend fun send() {
        val rec = recorder ?: return
        recorder = null
        val target = file
        file = null
        val duration = (android.os.SystemClock.elapsedRealtime() - started).coerceAtMost(30000)
        try {
            try { rec.stop() } finally { rec.release() }
            require(duration >= 300 && target != null && target.length() in 1..150000) { "Grabación demasiado corta" }
            val uid = Repo.ensureSignedIn()
            notes.add(mapOf("uid" to uid, "audio" to Blob.fromBytes(target.readBytes()),
                "durationMs" to duration, "createdAt" to FieldValue.serverTimestamp())).await()
        } finally { target?.delete() }
    }

    suspend fun play(note: Note, completed: () -> Unit) {
        stopPlayback()
        val bytes = notes.document(note.id).get().await().getBlob("audio")?.toBytes() ?: error("Audio no disponible")
        require(bytes.size in 1..150000)
        val target = File.createTempFile("play-", ".m4a", context.cacheDir)
        playingFile = target
        try {
            target.writeBytes(bytes)
            player = MediaPlayer().apply {
                setDataSource(target.absolutePath)
                setOnCompletionListener { stopPlayback(); completed() }
                setOnErrorListener { _, _, _ -> stopPlayback(); completed(); true }
                prepare(); start()
            }
        } catch (e: Exception) { stopPlayback(); throw e }
    }

    suspend fun delete(note: Note) { notes.document(note.id).delete().await() }
    fun stopPlayback() { player?.release(); player = null; playingFile?.delete(); playingFile = null }
    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }; recorder = null
        file?.delete(); file = null
    }
    fun close() { cancel(); stopPlayback() }
}
