package com.tony.nosotros.update

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Source
import com.tony.nosotros.BuildConfig
import com.tony.nosotros.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** REST reads avoid filling Firestore's offline cache with APK fragments. */
object PrivateApk {
    const val CHUNK_SIZE = 512 * 1024
    const val MAX_SIZE = 100L * 1024 * 1024
    fun validManifest(size: Long, count: Int, chunkSize: Int, hash: String): Boolean =
        size in 1..MAX_SIZE && chunkSize == CHUNK_SIZE &&
            count == ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt() && hash.matches(Regex("[a-f0-9]{64}"))

    suspend fun download(version: String, path: String, target: File) = withContext(Dispatchers.IO) {
        require(version.matches(Regex("[0-9]+\\.[0-9]+(\\.[0-9]+)?")))
        require(path == "firestore://releases/$version")
        check(Repo.authorizedSession)
        val doc = Repo.db.collection("releases").document(version).get(Source.SERVER).await()
        check(doc.getBoolean("ready") == true && doc.getString("apkPath") == path)
        val size = doc.getLong("size") ?: error("Tamaño ausente")
        val count = (doc.getLong("chunkCount") ?: 0).toInt()
        val chunkSize = (doc.getLong("chunkSize") ?: 0).toInt()
        val hash = doc.getString("sha256").orEmpty()
        require(validManifest(size, count, chunkSize, hash))
        val user = FirebaseAuth.getInstance().currentUser ?: error("Inicia sesión")
        var token = user.getIdToken(false).await().token ?: error("Sesión inválida")
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        target.outputStream().use { output ->
            for (index in 0 until count) {
                coroutineContext.ensureActive()
                check(Repo.authorizedSession && Repo.uid == user.uid)
                val id = index.toString().padStart(6, '0')
                val endpoint = "https://firestore.googleapis.com/v1/projects/${BuildConfig.FB_PROJECT_ID}/databases/(default)/documents/releases/$version/chunks/$id"
                var bytes: ByteArray? = null
                for (attempt in 0..1) {
                    val connection = URL(endpoint).openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 20_000; connection.readTimeout = 60_000
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    try {
                        if (connection.responseCode == 401 && attempt == 0) {
                            token = user.getIdToken(true).await().token ?: error("Sesión caducada")
                            continue
                        }
                        check(connection.responseCode == 200) { "Actualización no autorizada o cuota agotada" }
                        val json = connection.inputStream.bufferedReader().use { reader ->
                            val body = StringBuilder(); val buffer = CharArray(8192)
                            while (true) { val n = reader.read(buffer); if (n < 0) break
                                body.append(buffer, 0, n); require(body.length <= 800000) }
                            JSONObject(body.toString())
                        }
                        val fields = json.getJSONObject("fields")
                        require(fields.getJSONObject("index").getString("integerValue").toInt() == index)
                        bytes = Base64.decode(fields.getJSONObject("data").getString("bytesValue"), Base64.DEFAULT)
                        break
                    } finally { connection.disconnect() }
                }
                val chunk = bytes ?: error("Fragmento ausente")
                val expected = minOf(CHUNK_SIZE.toLong(), size - written).toInt()
                require(chunk.size == expected)
                output.write(chunk); digest.update(chunk); written += chunk.size
            }
        }
        require(written == size && digest.digest().joinToString("") { "%02x".format(it) } == hash) { "APK incompleto o alterado" }
    }
}
