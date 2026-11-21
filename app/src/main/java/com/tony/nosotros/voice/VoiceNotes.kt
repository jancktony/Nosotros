package com.tony.nosotros.voice

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.withTimeout
import com.tony.nosotros.data.Repo
import java.io.File
import kotlinx.coroutines.tasks.await

class VoiceNotes(private val context: Context, private val pairId: String, private val actor: () -> String = { requireNotNull(Repo.uid) }) {
    data class Note(val id: String, val uid: String, val duration: Long, val received: Boolean = false)
    data class Draft(val id: String, val duration: Long)
    private val draftDirectory: File get() {
        val uid = actor()
        val key = java.security.MessageDigest.getInstance("SHA-256").digest("$pairId:$uid".toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "voice-drafts/$key").apply { mkdirs() }
    }
    fun pending(): List<Draft> = draftDirectory.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9-]+_[0-9]+\\.m4a")) }.map {
        Draft(it.nameWithoutExtension.substringBefore('_'), it.nameWithoutExtension.substringAfter('_').toLong())
    }
    private fun draftFile(draft: Draft): File {
        require(draft.id.matches(Regex("[a-f0-9-]+")) && draft.duration in 300L..30000L)
        return File(draftDirectory,"${draft.id}_${draft.duration}.m4a")
    }
    fun discard(draft: Draft) { draftFile(draft).delete() }
    private val notes get() = Repo.db.collection("pairs").document(pairId).collection("voiceNotes")
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L
    private var player: MediaPlayer? = null
    private var playingFile: File? = null

    fun listen(result: (List<Note>) -> Unit, error: () -> Unit): ListenerRegistration =
        notes.orderBy("createdAt", Query.Direction.DESCENDING).limit(30).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, e ->
            if (e != null || Repo.uid != actor()) error() else {
                val uid = Repo.uid
                val drafts=pending().associateBy {it.id}
                snapshot?.documents.orEmpty().forEach { doc ->
                    if(!doc.metadata.isFromCache && !doc.metadata.hasPendingWrites() && doc.getString("uid")==uid) {
                        drafts[doc.id]?.let { draft ->
                            val target=draftFile(draft)
                            if(target.exists() && doc.getBlob("audio")?.toBytes()?.contentEquals(target.readBytes()) == true)target.delete()
                        }
                    }
                    if (!doc.metadata.isFromCache && !doc.metadata.hasPendingWrites() && uid != null && doc.getString("uid") != uid && doc.getString("receivedBy") != uid)
                        doc.reference.update(mapOf("receivedBy" to uid, "receivedAt" to FieldValue.serverTimestamp()))
                }
                result(snapshot?.documents.orEmpty().map {
                    Note(it.id, it.getString("uid").orEmpty(), it.getLong("durationMs") ?: 0, it.getString("receivedBy") != null)
                })
            }
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
        val target = file; file = null
        val duration = (android.os.SystemClock.elapsedRealtime() - started).coerceAtMost(30000)
        try {
            try { rec.stop() } finally { rec.release() }
            require(duration >= 300 && target != null && target.length() in 1..150000) { "Grabación demasiado corta" }
            val draft = Draft(java.util.UUID.randomUUID().toString(), duration)
            val retained=draftFile(draft);val temporary=File(retained.parentFile,retained.name+".tmp")
            try { target.copyTo(temporary); check(temporary.renameTo(retained)) } finally { temporary.delete() }
            target.delete()
            sendDraft(draft)
        } finally { target?.delete() }
    }

    /** Same document id on retries: a lost acknowledgement must not duplicate the note. */
    suspend fun sendDraft(draft: Draft) = withTimeout(12_000) {
        val target = draftFile(draft)
        val bytes = target.readBytes(); require(bytes.size in 1..150000)
        val uid = Repo.ensureSignedIn(); require(uid == actor())
        val ref = notes.document(draft.id)
        val existing = ref.get(Source.SERVER).await()
        if (!existing.exists()) ref.set(mapOf("uid" to uid, "audio" to Blob.fromBytes(bytes),
            "durationMs" to draft.duration, "createdAt" to FieldValue.serverTimestamp())).await()
        else require(existing.getString("uid") == uid && existing.getBlob("audio")?.toBytes()?.contentEquals(bytes) == true)
        target.delete(); Unit
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
