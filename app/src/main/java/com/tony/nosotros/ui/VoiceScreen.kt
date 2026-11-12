package com.tony.nosotros.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tony.nosotros.App
import com.tony.nosotros.data.Feature
import com.tony.nosotros.util.TimeFmt
import com.tony.nosotros.voice.VoiceNotes
import com.tony.nosotros.voice.WalkieHub
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VoiceScreen(vm: MainViewModel) {
    val direct = LocalFeatures.current.enabled(Feature.WALKIE)
    val saved = LocalFeatures.current.enabled(Feature.VOICE_NOTES)
    val pair by vm.pairId.collectAsState()
    val uid by vm.uid.collectAsState()
    val partner by vm.partner.collectAsState()
    val admin by vm.isAdmin.collectAsState()
    if ((!direct && !saved) || pair == null || uid == null) {
        Text("El administrador todavía no ha liberado esta función.", Modifier.padding(24.dp)); return
    }
    key(pair, uid) { VoicePanel(pair!!, uid!!, partner?.name?.ifBlank { null } ?: "Tu pareja", direct, saved, admin) }
}

@Composable
internal fun VoicePanel(pair: String, uid: String, partner: String, direct: Boolean, saved: Boolean, admin: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val radio by WalkieHub.state.collectAsState()
    var allowRelay by remember { mutableStateOf(App.instance.prefs.allowVoiceRelay) }
    var confirmRelay by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    val voice = remember { VoiceNotes(context.applicationContext, pair) }
    var tab by remember { mutableIntStateOf(if (direct) 0 else 1) }
    var message by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<String?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var notes by remember { mutableStateOf(emptyList<VoiceNotes.Note>()) }
    var seconds by remember { mutableIntStateOf(0) }
    val audioManager = remember { context.getSystemService(android.media.AudioManager::class.java) }
    val interrupt by rememberUpdatedState {
        voice.close(); recording = false; playJob?.cancel(); playing = null
    }
    val noteFocus = remember {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change -> if (change < 0) interrupt() }, android.os.Handler(android.os.Looper.getMainLooper())).build()
    }
    fun focusNotes(): Boolean = audioManager.requestAudioFocus(noteFocus) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        message = if (it) "Listo. Mantén pulsado para hablar." else "Permite el micrófono para poder hablar."
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { WalkieHub.enable(context) }
    fun microphone(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        micPermission.launch(Manifest.permission.RECORD_AUDIO); return false
    }
    fun receive(enabled: Boolean) {
        if (!enabled) WalkieHub.disable(context)
        else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else WalkieHub.enable(context)
    }
    fun send() {
        if (!recording) return
        recording = false; sending = true
        audioManager.abandonAudioFocusRequest(noteFocus)
        scope.launch {
            try { voice.send(); message = "Nota de voz enviada" }
            catch (_: Exception) { message = "No se pudo enviar. Revisa tu conexión o graba un poco más." }
            finally { sending = false }
        }
    }
    LaunchedEffect(direct, saved) { if (!direct) tab = 1 else if (!saved) tab = 0 }
    LaunchedEffect(recording, radio.talking) {
        seconds = 0
        if (radio.talking) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        while (recording || radio.talking) { delay(1000); seconds++ }
    }
    DisposableEffect(lifecycle, direct) {
        WalkieHub.foregroundOpen = direct
        WalkieHub.prepare()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { WalkieHub.foregroundOpen = direct; if (direct && App.instance.prefs.receiveWalkie) WalkieHub.enable(context); WalkieHub.prepare() }
            if (event == Lifecycle.Event.ON_PAUSE) { WalkieHub.foregroundOpen = false; WalkieHub.release(); voice.close(); recording = false; playJob?.cancel(); playing = null; audioManager.abandonAudioFocusRequest(noteFocus) }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); WalkieHub.foregroundOpen = false; WalkieHub.release(); voice.close(); playJob?.cancel(); audioManager.abandonAudioFocusRequest(noteFocus) }
    }
    DisposableEffect(saved) {
        val listener = if (saved) voice.listen({ notes = it }, { message = "No se pudieron cargar las notas de voz" }) else null
        onDispose { listener?.remove(); voice.close(); recording = false; playJob?.cancel(); playing = null }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.TopCenter) {
        val compact = maxHeight < 650.dp
        val tiny = maxHeight < 460.dp
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()
            .then(if (tab == 0 && direct) Modifier.fillMaxHeight() else Modifier)
            .statusBarsPadding().then(if (tab == 0 && direct) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(if(tiny) 10.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if(tiny) 4.dp else 8.dp)) {
            if (!tiny) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Walkie-talkie", color = C.text, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    if (!compact) Text("Una voz más cerca de $partner", color = C.sub, fontSize = 13.sp)
                }
                Box(Modifier.clip(CircleShape).background(C.me.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("PRIVADO", color = C.me, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            if (direct && saved) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface).padding(5.dp)) {
                if (direct) RadioTab("En directo", tab == 0, Modifier.weight(1f)) { WalkieHub.release(); voice.close(); recording = false; playing = null; tab = 0; WalkieHub.prepare() }
                if (saved) RadioTab("Notas de voz", tab == 1, Modifier.weight(1f)) { WalkieHub.release(); tab = 1 }
            }
            message?.let { Text(it, color = C.me, fontSize = 13.sp) }
            if (tab == 0 && direct) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface)
                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recepción", Modifier.weight(1f), color = C.text, fontWeight = FontWeight.SemiBold)
                    Switch(radio.enabled, { receive(it) })
                    TextButton(onClick = { if (!radio.talking) showOptions = true }) { Text("Opciones", color = C.me) }
                }
                if (showOptions) AlertDialog(onDismissRequest = { showOptions = false },
                    title = { Text("Opciones de voz") },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                if (LocalFeatures.current.enabled(Feature.WALKIE_RELAY)) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Modo redes difíciles", color = C.text)
                        Text("Opcional en ambos teléfonos · fragmentos privados temporales", color = C.sub, fontSize = 11.sp)
                    }
                    Switch(allowRelay, { value -> if (value) confirmRelay = true else {
                        allowRelay = false; App.instance.prefs.allowVoiceRelay = false; WalkieHub.service?.refreshRelay()
                    } })
                }
                if (confirmRelay) AlertDialog(onDismissRequest = { confirmRelay = false },
                    title = { Text("¿Activar voz en redes difíciles?") },
                    text = { Text("Cuando la red no permita el canal directo, envía fragmentos de voz comprimida privados mediante Firebase. Se borran al recibirlos o a los 15 segundos; si las apps se cierran, el borrado pendiente continúa al abrirlas. Consume la cuota gratuita y puede tener más retraso. Ambos deben activarlo.") },
                    confirmButton = { TextButton(onClick = {
                        allowRelay = true; App.instance.prefs.allowVoiceRelay = true; confirmRelay = false; WalkieHub.service?.refreshRelay()
                    }) { Text("Activar en mi teléfono") } },
                    dismissButton = { TextButton(onClick = { confirmRelay = false }) { Text("Cancelar") } })

                        Text("Voz comprimida de bajo consumo. Ambos necesitan una versión compatible y recepción activada. Revisa el volumen multimedia.", color = C.sub, fontSize = 13.sp)
                        Text("El canal se cierra tras 1 minuto sin voz; hasta 30 s por pulsación.", color = C.sub, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 70)
                        tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 300)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
                    }) { Text("Probar altavoz", color = C.me) }
                    TextButton(onClick = { WalkieHub.service?.reconnect() }) { Text("Reconectar", color = C.me) }
                }

                    } }, confirmButton = { TextButton(onClick = { showOptions = false }) { Text("Listo") } })
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (radio.connected) C.me else C.sub))
                    Text(when { radio.talking -> "TRANSMITIENDO"; radio.connected -> "CANAL LISTO"; radio.enabled -> "EN ESPERA"; else -> "RECEPCIÓN APAGADA" },
                        color = if (radio.connected) C.me else C.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment=Alignment.Center) {
                RadioMic(radio.talking, radio.enabled && !recording && !sending,
                    onStart = {
                        if (!microphone()) false else {
                            playJob?.cancel(); voice.stopPlayback(); playing = null
                            audioManager.abandonAudioFocusRequest(noteFocus); WalkieHub.press(); true
                        }
                    }, onRelease = { WalkieHub.release() }, onCancel = { WalkieHub.release() }, extent = minOf(maxHeight,maxWidth,226.dp).coerceAtLeast(48.dp))
                }
                Text(if (radio.talking) "0:${seconds.toString().padStart(2, '0')}" else "Mantén pulsado para hablar",
                    color = C.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                if (!tiny) Text(if (radio.talking) "Suelta para terminar" else if (!radio.enabled) "Primero activa la recepción" else radio.message,
                    color = C.sub, fontSize = 13.sp)
            } else if (saved) {
                Text("Déjale tu voz", color = C.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("La escuchará después, cuando pueda.", color = C.sub, fontSize = 13.sp)
                RadioMic(recording, !sending && !radio.talking,
                    onStart = {
                        if (!microphone() || !focusNotes()) false else runCatching {
                            WalkieHub.release(); playJob?.cancel(); playing = null
                            voice.start { send() }; recording = true; true
                        }.getOrElse { message = "No se pudo abrir el micrófono"; false }
                    }, onRelease = { send() }, onCancel = { voice.cancel(); recording = false })
                Text(if (recording) "Grabando · 0:${seconds.toString().padStart(2, '0')}" else if (sending) "Enviando…" else "Mantén pulsado para grabar",
                    color = C.text, fontWeight = FontWeight.SemiBold)
                Text("Suelta para enviar · máximo 30 s", color = C.sub, fontSize = 12.sp)
                if (playing != null) TextButton(onClick = { playJob?.cancel(); voice.stopPlayback(); playing = null }) { Text("Detener reproducción") }
                if (notes.isEmpty()) Text("Aquí aparecerán sus notas de voz", color = C.sub, modifier = Modifier.padding(16.dp))
                notes.forEach { note ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !recording && !sending && !radio.talking, onClick = {
                            if (!focusNotes()) return@TextButton
                            playJob?.cancel(); playing = note.id
                            playJob = scope.launch {
                                try { voice.play(note) { playing = null; audioManager.abandonAudioFocusRequest(noteFocus) } }
                                catch (_: Exception) { playing = null; message = "No se pudo reproducir la nota" }
                            }
                        }) { Text(if (playing == note.id) "■" else "▶", color = C.me, fontSize = 22.sp) }
                        Column(Modifier.weight(1f)) {
                            Text(if (note.uid == uid) "Tu voz" else partner, color = C.text, fontWeight = FontWeight.SemiBold)
                            Text("${maxOf(1, note.duration / 1000)} segundos", color = C.sub, fontSize = 12.sp)
                        }
                        if (note.uid == uid || admin) TextButton(onClick = { scope.launch {
                            runCatching { voice.delete(note) }.onFailure { message = "No se pudo borrar la nota" }
                        } }) { Text("Borrar", color = C.sub, fontSize = 11.sp) }
                    }
                }
            }
            if (tab != 0 || !direct) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RadioTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) C.me.copy(alpha = .15f) else Color.Transparent)
        .clickable(onClick = onClick).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) C.me else C.sub, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun RadioMic(active: Boolean, enabled: Boolean, onStart: () -> Boolean, onRelease: () -> Unit, onCancel: () -> Unit, extent: androidx.compose.ui.unit.Dp = 226.dp) {
    val start by rememberUpdatedState(onStart); val release by rememberUpdatedState(onRelease); val cancel by rememberUpdatedState(onCancel)
    val glow by animateFloatAsState(if (active) .28f else .06f, tween(250), label = "mic-glow")
    val accent = if (enabled) C.me else C.sub
    Box(Modifier.size(extent), contentAlignment = Alignment.Center) {
        Box(Modifier.size(extent * .97f).clip(CircleShape).background(accent.copy(alpha = glow)))
        Box(Modifier.size(extent * .83f).clip(CircleShape).background(accent.copy(alpha = glow + .05f)))
        Box(Modifier.size(extent * .71f).clip(CircleShape).background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = .7f))))
            .border(1.dp, Color.White.copy(alpha = .15f), CircleShape)
            .testTag("walkie-hold")
            .holdToTalk(enabled, { start() }, { release() }, { cancel() }), contentAlignment = Alignment.Center) {
            val ink = C.meInk
            Canvas(Modifier.size(extent * .23f, extent * .29f)) {
                val stroke = 4.dp.toPx()
                drawRoundRect(ink, Offset(size.width * .32f, 0f), Size(size.width * .36f, size.height * .62f), CornerRadius(size.width * .18f))
                drawArc(ink, 0f, 180f, false, Offset(size.width * .16f, size.height * .30f), Size(size.width * .68f, size.height * .46f), style = Stroke(stroke))
                drawLine(ink, Offset(size.width / 2, size.height * .76f), Offset(size.width / 2, size.height * .96f), stroke)
                drawLine(ink, Offset(size.width * .32f, size.height * .96f), Offset(size.width * .68f, size.height * .96f), stroke)
            }
        }
    }
}

internal fun Modifier.holdToTalk(enabled: Boolean, start: () -> Boolean, release: () -> Unit, cancel: () -> Unit): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            down.consume()
            var released = false
            val started = start()
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val finger = event.changes.firstOrNull { it.id == down.id }
                    event.changes.forEach { it.consume() }
                    if (finger == null || !finger.pressed) { released = true; break }
                } while (true)
            } finally { if (started) { if (released) release() else cancel() } }
        }
    }
