package com.tony.nosotros.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.tony.nosotros.data.Feature
import com.tony.nosotros.data.Repo
import com.tony.nosotros.voice.VoiceNotes
import com.tony.nosotros.voice.WalkieConnection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun VoiceScreen(vm: MainViewModel) {
    val direct = LocalFeatures.current.enabled(Feature.WALKIE)
    val saved = LocalFeatures.current.enabled(Feature.VOICE_NOTES)
    val pair by vm.pairId.collectAsState()
    val uid by vm.uid.collectAsState()
    val partner by vm.partnerUid.collectAsState()
    val admin by vm.isAdmin.collectAsState()
    if ((!direct && !saved) || pair == null || uid == null) {
        Text("El administrador todavía no ha liberado esta función.", Modifier.padding(24.dp)); return
    }
    key(pair, uid) { VoicePanel(pair!!, uid!!, partner, direct, saved, admin) }
}

@Composable
private fun VoicePanel(pair: String, uid: String, partner: String?, direct: Boolean, saved: Boolean, admin: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Desconectado") }
    var ready by remember { mutableStateOf(false) }
    var talking by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<String?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var incoming by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var notes by remember { mutableStateOf(emptyList<VoiceNotes.Note>()) }
    val voice = remember { VoiceNotes(context.applicationContext, pair) }
    val connection = remember(direct) {
        if (direct) runCatching { WalkieConnection(context, pair, uid) { text, connected ->
            message = text; ready = connected; if (!connected) talking = false
        } }.getOrNull() else null
    }
    val audioManager = remember { context.getSystemService(android.media.AudioManager::class.java) }
    val interrupt by rememberUpdatedState {
        connection?.end("Audio interrumpido"); voice.close(); recording = false; talking = false
        playJob?.cancel(); playing = null; ready = false
    }
    val focusRequest = remember {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change -> if (change <= 0) interrupt() }, android.os.Handler(android.os.Looper.getMainLooper()))
            .build()
    }
    fun focus(): Boolean {
        val granted = audioManager.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) message = "Otra aplicación está usando el audio. Inténtalo después."
        return granted
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        message = if (it) "Micrófono autorizado. Mantén pulsado para hablar." else "Autoriza el micrófono para poder hablar."
    }
    fun permitted(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return focus()
        permission.launch(Manifest.permission.RECORD_AUDIO); return false
    }
    fun sendNote() {
        if (!recording) return
        recording = false; sending = true
        scope.launch {
            try { voice.send(); message = "Nota de voz enviada" }
            catch (_: Exception) { message = "No se pudo enviar. Comprueba tu conexión o graba un poco más." }
            finally { sending = false }
        }
    }
    DisposableEffect(connection, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                connection?.end(); voice.close(); recording = false; talking = false
                playJob?.cancel(); playing = null; ready = false
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer); connection?.close(); voice.close(); playJob?.cancel()
            recording = false; talking = false; ready = false; playing = null
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }
    DisposableEffect(saved) {
        val listener = if (saved) voice.listen({ notes = it }, { message = "No se pudieron cargar las notas. Revisa conexión y permisos del servicio." }) else null
        onDispose { listener?.remove(); voice.close(); notes = emptyList(); recording = false; playing = null; playJob?.cancel() }
    }
    DisposableEffect(direct, connection) {
        val listener = if (direct && connection != null) Repo.db.collection("pairs").document(pair).collection("walkieCalls")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(10).addSnapshotListener { snapshot, error ->
                if (error != null) message = "No se pudo abrir el canal. Revisa conexión y permisos del servicio."
                incoming = snapshot?.documents?.firstOrNull {
                    it.getString("to") == uid && it.getString("answer") == null &&
                        (it.getTimestamp("createdAt")?.toDate()?.time ?: 0) > System.currentTimeMillis() - 60000
                }
            } else null
        onDispose { listener?.remove(); incoming = null }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Walkie-talkie", style = MaterialTheme.typography.headlineMedium)
        Text(message)
        if (direct) {
            Text("En directo", style = MaterialTheme.typography.titleLarge)
            Text("Ambos deben abrir Voz y aceptar la conexión. El audio en directo no se guarda. Cada pulsación dura hasta 30 segundos; la sesión, 5 minutos.")
            if (connection == null) Text("Este dispositivo no pudo iniciar la conexión de voz.")
            Row {
                TextButton(enabled = partner != null && connection != null && !connection.active && !recording && !sending,
                    onClick = { if (permitted()) { voice.stopPlayback(); playing = null; connection?.dial(partner!!) } }) { Text("Conectar") }
                TextButton(onClick = { connection?.end(); talking = false }, enabled = connection?.active == true) { Text("Desconectar") }
            }
            if (incoming != null && connection?.active == false) {
                Text("Tu pareja quiere abrir el canal")
                Row {
                    TextButton(enabled = !recording && !sending, onClick = { if (permitted()) { voice.stopPlayback(); playing = null; connection.accept(incoming!!) } }) { Text("Aceptar") }
                    TextButton(onClick = { incoming?.reference?.delete(); incoming = null }) { Text("Rechazar") }
                }
            }
            HoldToTalk(if (talking) "Hablando… suelta para terminar" else "Mantén pulsado para hablar", ready && !recording && !sending,
                onStart = { if (permitted()) { talking = true; connection?.talk { talking = false }; true } else false },
                onRelease = { connection?.stopTalking(); talking = false },
                onCancel = { connection?.stopTalking(); talking = false })
        }
        if (saved) {
            Text("Notas de voz", style = MaterialTheme.typography.titleLarge)
            Text("Se guardan para escucharlas después. Mantén pulsado y suelta para enviar. Si sales de la pantalla, la grabación se cancela.")
            HoldToTalk(if (recording) "Grabando… suelta para enviar" else if (sending) "Enviando…" else "Mantén pulsado para grabar",
                !sending && connection?.active != true,
                onStart = {
                    if (!permitted()) false else runCatching { voice.start { sendNote() }; recording = true; true }
                        .getOrElse { message = "No se pudo abrir el micrófono"; false }
                }, onRelease = { sendNote() }, onCancel = { voice.cancel(); recording = false })
            if (playing != null) TextButton(onClick = { playJob?.cancel(); voice.stopPlayback(); playing = null }) { Text("Detener reproducción") }
            notes.forEach { note ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !recording && !sending && connection?.active != true, onClick = {
                        if (!focus()) return@TextButton
                        playJob?.cancel(); playing = note.id
                        playJob = scope.launch {
                            try { voice.play(note) { playing = null } }
                            catch (_: Exception) { playing = null; message = "No se pudo reproducir la nota" }
                        }
                    }) { Text("▶ ${if (note.uid == uid) "Tú" else "Tu pareja"} · ${maxOf(1, note.duration / 1000)} s") }
                    if (note.uid == uid || admin) TextButton(onClick = { scope.launch {
                        runCatching { voice.delete(note) }.onFailure { message = "No se pudo borrar la nota" }
                    } }) { Text("Borrar") }
                }
            }
        }
    }
}

@Composable
private fun HoldToTalk(label: String, enabled: Boolean, onStart: () -> Boolean, onRelease: () -> Unit, onCancel: () -> Unit) {
    val start by rememberUpdatedState(onStart)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    Box(Modifier.fillMaxWidth().background(if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
        .pointerInput(enabled) { if (enabled) detectTapGestures(onPress = {
            if (start()) {
                var released = false
                try { released = tryAwaitRelease() }
                finally { if (released) release() else cancel() }
            }
        }) }.padding(24.dp)) { Text(label) }
}
