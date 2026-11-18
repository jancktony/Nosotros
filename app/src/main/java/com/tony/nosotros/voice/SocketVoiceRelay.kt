package com.tony.nosotros.voice

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.tony.nosotros.BuildConfig
import com.tony.nosotros.data.Repo
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Persistent private fallback; starting this object never opens the microphone. */
internal class SocketVoiceRelay(
    private val pair: String, private val uid: String,
    private val clock: VoiceClock,
    private val database: FirebaseFirestore = Repo.db,
    private val authorized: () -> Boolean = { Repo.authorizedSession && Repo.uid == uid },
    private val received: ((ByteArray) -> Unit)? = null,
    private val focus: () -> Boolean,
    private val status: (String, Boolean) -> Unit
) {
    private data class Config(val url: String, val room: String, val key: ByteArray,
        val token: String, val role: String, val expiresAt: Long)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var reconnectJob: Job? = null
    private var socket: WebSocket? = null
    private var signal: CompletableDeferred<Unit>? = null
    private var engine: PushAudio? = null
    private var epoch = 0
    private var lastAudio = 0L
    @Volatile private var closed = false
    @Volatile private var opened = false
    @Volatile private var peerReady = false
    @Volatile private var config: Config? = null
    @Volatile private var cipher: VoiceCipher? = null
    val ready get() = !closed && opened && peerReady && config != null && authorized()
    fun start() {
        if (job?.isActive == true || closed) return
        job = scope.launch {
            var attempts = 0
            while (isActive && !closed && authorized()) {
                var maintenance: Job? = null
                try {
                    val loaded = withTimeoutOrNull(12_000) { load() }
                    if (loaded == null) {
                        report("Preparando respaldo privado…")
                        delay(60_000); continue
                    }
                    config = loaded
                    cipher = VoiceCipher(loaded.key, loaded.room)
                    val done = CompletableDeferred<Unit>(); signal = done
                    val connected = CompletableDeferred<Unit>()
                    val generation = ++epoch
                    socket = client.newWebSocket(Request.Builder().url(loaded.url)
                        .header("Authorization", "Bearer " + loaded.token).build(), object : WebSocketListener() {
                        override fun onOpen(ws: WebSocket, response: Response) { handler.post {
                            if (!valid(generation)) { ws.close(1000, null); return@post }
                            opened = true; attempts = 0; connected.complete(Unit)
                        } }
                        override fun onMessage(ws: WebSocket, text: String) { handler.post {
                            if (!valid(generation) || text.length > 4096) return@post
                            runCatching {
                                val data = JSONObject(text)
                                val time = data.optLong("now")
                                if (time > 0) clock.synchronize(time)
                                when (data.optString("type")) {
                                    "ready" -> {
                                        peerReady = data.optBoolean("ready")
                                        report(if (peerReady) "Canal privado listo" else "Esperando recepción de ambos teléfonos")
                                    }
                                }
                            }
                        } }
                        override fun onMessage(ws: WebSocket, bytes: ByteString) {
                            val envelope = bytes.toByteArray()
                            if (envelope.size !in 48..436) return
                            handler.post {
                                if (!valid(generation) || !ready) return@post
                                val role = if (loaded.role == "owner") "partner" else "owner"
                                val packet = cipher?.open(envelope, role, clock.now()) ?: return@post
                                if (VoiceWire.frames(packet) == null || !focus()) return@post
                                lastAudio = SystemClock.elapsedRealtime()
                                runCatching { received?.invoke(packet) ?: audio().receive(packet) }
                                    .onFailure { report("No se pudo abrir el audio. Revisa el volumen multimedia.") }
                            }
                        }
                        override fun onClosed(ws: WebSocket, code: Int, reason: String) { handler.post {
                            if (!valid(generation)) return@post
                            opened = false; peerReady = false; report("Recuperando canal privado…"); done.complete(Unit)
                        } }
                        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { handler.post {
                            if (!valid(generation)) return@post
                            opened = false; peerReady = false
                            report("Recuperando canal privado…")
                            connected.completeExceptionally(java.io.IOException("Channel unavailable"))
                            done.complete(Unit)
                        } }
                    })
                    withTimeout(15_000) { connected.await() }
                    maintenance = launch {
                        var ticks = 0
                        while (isActive) {
                            delay(15_000)
                            if (!authorized()) { done.complete(Unit); break }
                            if (engine != null && SystemClock.elapsedRealtime() - lastAudio >= 60_000) {
                                engine?.close(); engine = null
                            }
                            if (++ticks % 8 == 0) socket?.send("{\"type\":\"sync\"}")
                            if (ticks % 120 == 0 || clock.now() > (config?.expiresAt ?: Long.MAX_VALUE) - 1_800_000L) {
                                val refreshed = try { withTimeoutOrNull(10_000) { load() } }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { null }
                                if (refreshed != null && refreshed.token != config?.token &&
                                    refreshed.room == loaded.room && refreshed.role == loaded.role &&
                                    refreshed.key.contentEquals(loaded.key)) {
                                    socket?.send(JSONObject().put("type","reauth").put("ticket",refreshed.token).toString())
                                    config = refreshed
                                }
                            }
                        }
                    }
                    done.await()
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                    report("Recuperando canal privado…")
                } catch (_: Exception) { report("Recuperando canal privado…") }
                finally {
                    ++epoch; maintenance?.cancel(); socket?.cancel(); socket = null
                    opened = false; peerReady = false
                    engine?.close(); engine = null
                    if (!closed) report("Recuperando canal privado…")
                }
                if (!closed) {
                    attempts++
                    delay(minOf(10_000L, 500L * (1L shl minOf(attempts, 4))))
                }
            }
        }
    }
    private fun valid(generation: Int) = !closed && generation == epoch && authorized()
    private fun report(message: String) { if (!closed) status(message, ready) }
    private suspend fun load(): Config? {
        if (!authorized()) return null
        val pairDoc = database.collection("pairs").document(pair).get(Source.SERVER).await()
        val stream = pairDoc.get("voiceStream") as? Map<*, *> ?: return null
        if (stream["enabled"] != true) return null
        val url = stream["url"] as? String ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val localTest = BuildConfig.DEBUG && uri.scheme == "ws" && uri.host == "10.0.2.2"
        if (!localTest && (uri.scheme != "wss" || uri.host.isNullOrBlank())) return null
        if (uri.userInfo != null || uri.fragment != null || uri.query != null || uri.path != "/voice") return null
        val room = stream["room"] as? String ?: return null
        val key = runCatching { Base64.getDecoder().decode(stream["key"] as? String ?: "") }.getOrNull() ?: return null
        if (key.size != 32 || !room.matches(Regex("[a-f0-9]{64}"))) return null
        val ticket = database.collection("pairs").document(pair).collection("voiceTickets").document(uid)
            .get(Source.SERVER).await()
        val token = ticket.getString("token") ?: return null
        val role = ticket.getString("role") ?: return null
        if (ticket.getString("room") != room || role !in listOf("owner","partner") ||
            (role == "owner") != (pairDoc.getString("admin") == uid) || token.length !in 100..5000) return null
        check(authorized())
        return Config(url, room, key, token, role, ticket.getLong("expiresAt") ?: 0)
    }
    private fun audio(): PushAudio {
        engine?.let { return it }
        return PushAudio({ packet -> enqueue(packet) }, { lastAudio = SystemClock.elapsedRealtime() },
            { handler.post { report("No se pudo abrir el audio. Revisa el permiso y el volumen multimedia.") } })
            .also { engine = it; lastAudio = SystemClock.elapsedRealtime() }
    }
    internal fun enqueue(packet: ByteArray): Boolean {
        if (!ready || VoiceWire.frames(packet) == null) return false
        val own = config ?: return false
        val encrypted = runCatching { cipher?.seal(packet, own.role, clock.now()) }.getOrNull() ?: return false
        val ws = socket ?: return false
        if (ws.queueSize() > 4096) return false // Never accumulate delayed speech.
        return ws.send(encrypted.toByteString())
    }
    fun talk(stopped: () -> Unit): Boolean {
        if (!ready) return false
        lastAudio = SystemClock.elapsedRealtime()
        return try { audio().start { handler.post(stopped) }; true }
        catch (_: Exception) { stopped(); report("No se pudo usar el micrófono"); false }
    }
    fun stopTalking() { engine?.stop() }
    fun reconnect() {
        if (closed) return
        opened = false; peerReady = false
        report("Recuperando canal privado…")
        if (reconnectJob?.isActive == true) return
        signal?.complete(Unit); socket?.cancel()
        reconnectJob = scope.launch {
            job?.cancelAndJoin()
            job = null
            if (!closed) start()
        }
    }
    fun close() {
        closed = true; ++epoch
        socket?.close(1000, null); job?.cancel(); scope.cancel()
        socket = null; engine?.close(); engine = null
        cipher = null; config = null; opened = false; peerReady = false
    }
    companion object {
        private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(12, TimeUnit.SECONDS).build()
    }
}
