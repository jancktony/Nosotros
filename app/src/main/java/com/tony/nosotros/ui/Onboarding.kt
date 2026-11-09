package com.tony.nosotros.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tony.nosotros.data.Mode

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var step by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(Modifier.height(4.dp).weight(1f).clip(CircleShape).background(if (i <= step) C.me else C.surface2))
            }
        }
        when (step) {
            0 -> WelcomeStep(vm) { step = 1 }
            1 -> PermissionsStep { step = 2 }
            else -> PairStep(vm)
        }
    }
}

@Composable
private fun WelcomeStep(vm: MainViewModel, next: () -> Unit) {
    var name by remember { mutableStateOf(vm.prefs.myName) }
    var vehicle by remember { mutableStateOf(vm.prefs.vehicle) }
    Spacer(Modifier.height(24.dp))
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(C.surface2), contentAlignment = Alignment.Center) {
        Icon(Ic.Heart, null, tint = C.partner, modifier = Modifier.size(36.dp))
    }
    Text("Bienvenidos a Nosotros", style = MaterialTheme.typography.headlineMedium)
    Text("Vean dónde está el otro, reciban avisos al llegar y salir, y repasen su día. Precisa y cuidando la batería.",
        color = C.sub)
    OutlinedTextField(name, { name = it }, label = { Text("¿Cómo te llamas?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text("Tu vehículo habitual", color = C.sub, fontSize = 14.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Carro", vehicle == Mode.CAR, icon = Ic.Car) { vehicle = Mode.CAR }
        Chip("Moto", vehicle == Mode.MOTO, icon = Ic.Moto) { vehicle = Mode.MOTO }
    }
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Continuar", enabled = name.isNotBlank()) {
        vm.prefs.myName = name.trim(); vm.prefs.vehicle = vehicle; next()
    }
}

@Composable
private fun PermissionsStep(next: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    refresh.let { }

    val fine = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    val bg = granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val act = granted(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
    val notif = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
    val battery = isIgnoringBattery(ctx)

    val fineL = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val singleL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Text("Permisos", style = MaterialTheme.typography.headlineMedium)
    Text("Toca cada uno en orden. Son necesarios para que la ubicación funcione aunque la app esté cerrada.", color = C.sub)

    PermRow(Ic.Pin, "Ubicación precisa", "Elige “Mientras se usa la app” y “Precisa”.", fine) {
        fineL.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    PermRow(Ic.Clock, "Ubicación todo el tiempo", "Se abrirá un ajuste: elige “Permitir todo el tiempo”.", bg, enabled = fine) {
        singleL.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    PermRow(Ic.Walk, "Actividad física", "Para saber si vas a pie, en carro o quieto, y ahorrar batería.", act) {
        singleL.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }
    PermRow(Ic.Live, "Notificaciones", "Para avisarte cuando tu pareja llega o sale.", notif) {
        if (Build.VERSION.SDK_INT >= 33) singleL.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    PermRow(Ic.Battery, "Sin restricción de batería", "Evita que el teléfono cierre la app en segundo plano.", battery) {
        requestIgnoreBattery(ctx)
    }
    Spacer(Modifier.height(8.dp))
    PrimaryButton(if (fine && bg && act) "Continuar" else "Continuar de todos modos", enabled = fine) { next() }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, desc: String, ok: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface)
            .border(1.dp, if (ok) C.me.copy(alpha = 0.4f) else C.line, RoundedCornerShape(16.dp))
            .clickable(enabled = !ok && enabled, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint = if (ok) C.me else C.text)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, color = C.sub, fontSize = 13.sp)
        }
        if (ok) Icon(Ic.Check, "Listo", tint = C.me) else Text("Permitir", color = if (enabled) C.me else C.sub, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PairStep(vm: MainViewModel) {
    val code by vm.createdCode.collectAsState()
    val members by vm.members.collectAsState()
    val error by vm.error.collectAsState()
    val pairId by vm.pairId.collectAsState()
    var mode by remember { mutableStateOf(if (code != null) "create" else "") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Text("Vincular", style = MaterialTheme.typography.headlineMedium)

    // Cuenta recuperada (ya tenía pareja) o recién unido con código: terminar
    if (pairId != null && code == null) {
        Text("¡Listo! Ya están conectados.", color = C.me, fontWeight = FontWeight.SemiBold)
        PrimaryButton("Empezar") { vm.finishOnboarding() }
        return
    }

    when (mode) {
        "create" -> {
            Text("Pídele a tu pareja que instale la app y escriba este código:", color = C.sub)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface)
                    .border(1.dp, C.me.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (code == null) CircularProgressIndicator(color = C.me)
                else Text(code!!.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp, color = C.me, textAlign = TextAlign.Center)
            }
            val joined = members.size >= 2
            Text(if (joined) "¡Tu pareja se unió!" else "Esperando a que tu pareja se una… (el código dura 24 h)",
                color = if (joined) C.me else C.sub)
            PrimaryButton(if (joined) "Empezar" else "Empezar sin esperar", enabled = code != null) { vm.finishOnboarding() }
        }
        "join" -> {
            Text("Escribe el código de 6 dígitos que aparece en el teléfono de tu pareja.", color = C.sub)
            OutlinedTextField(
                input, { v -> input = v.filter { it.isDigit() }.take(6); vm.error.value = null },
                label = { Text("Código") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            error?.let { Text(it, color = C.danger, fontSize = 14.sp) }
            PrimaryButton(if (busy && error == null) "Conectando…" else "Unirme", enabled = input.length == 6 && !(busy && error == null)) {
                busy = true; vm.joinCode(input)
            }
            TextButton(onClick = { mode = "" }) { Text("Volver", color = C.sub) }
        }
        else -> {
            Text("Uno de los dos crea un código y el otro lo escribe en su teléfono.", color = C.sub)
            error?.let { Text(it, color = C.danger, fontSize = 14.sp) }
            PrimaryButton("Crear código") { mode = "create"; vm.createPair() }
            Button(
                onClick = { mode = "join" }, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(C.surface2, C.text),
            ) { Text("Tengo un código", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(C.me, C.meInk, C.surface2, C.sub),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}
