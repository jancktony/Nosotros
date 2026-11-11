package com.tony.nosotros.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tony.nosotros.data.Feature
import com.tony.nosotros.presence.UsagePermission

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresenceSettings(vm: MainViewModel) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val access by vm.features.collectAsState()
    val message by vm.presenceMessage.collectAsState()
    var appSharing by remember { mutableStateOf(vm.prefs.shareCurrentApp) }
    var sleepSharing by remember { mutableStateOf(vm.prefs.shareSleep) }
    var granted by remember { mutableStateOf(UsagePermission.granted(ctx)) }
    var confirm by remember { mutableStateOf<Feature?>(null) }
    var start by remember { mutableStateOf(vm.prefs.sleepStartHour) }
    var wake by remember { mutableStateOf(vm.prefs.wakeHour) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            granted = UsagePermission.granted(ctx)
            if (!granted && vm.prefs.shareCurrentApp) { vm.setPresence(false, false); appSharing = false }
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Text("Actividad compartida · voluntaria", color = C.text, style = MaterialTheme.typography.titleLarge)
    Text("Cada uno decide en su teléfono. Tu administrador solo habilita la disponibilidad; no puede activar tus permisos ni tu consentimiento.", color = C.sub)
    Text("Aplicación en uso", color = C.text, style = MaterialTheme.typography.titleMedium)
    Text("Comparte únicamente el nombre de la última app detectada, con retraso aproximado de 30 segundos. No lee mensajes, pantalla, contraseñas ni historial. En pantalla dividida puede mostrar solo la última app activa.", color = C.sub, fontSize = 13.sp)
    if (!access.enabled(Feature.APP_PRESENCE)) Text("Tu administrador aún no habilitó esta función para ti.", color = C.warn)
    if (!granted) TextButton(onClick = {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
            .onFailure { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }) { Text("Abrir permiso de acceso de uso", color = C.me) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(if (appSharing) "Compartiendo mi app" else "Compartir mi app", Modifier.weight(1f), color = C.text)
        Switch(appSharing, { enabled -> if (enabled) confirm = Feature.APP_PRESENCE
            else { appSharing = false; vm.setPresence(false, false) } },
            enabled = appSharing || (granted && access.enabled(Feature.APP_PRESENCE)))
    }
    HorizontalDivider()
    Text("Descanso estimado", color = C.text, style = MaterialTheme.typography.titleMedium)
    Text("Se estima a partir de inactividad nocturna y pantalla bloqueada. No confirma que estés dormido, no usa datos de salud ni tus alarmas. Sin señal reciente aparece sin datos. No envía alertas ni te despierta.", color = C.sub, fontSize = 13.sp)
    if (!access.enabled(Feature.SLEEP_STATUS)) Text("Tu administrador aún no habilitó esta función para ti.", color = C.warn)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(if (sleepSharing) "Compartiendo mi descanso estimado" else "Compartir descanso estimado", Modifier.weight(1f), color = C.text)
        Switch(sleepSharing, { enabled -> if (enabled) confirm = Feature.SLEEP_STATUS
            else { sleepSharing = false; vm.setPresence(true, false) } },
            enabled = sleepSharing || access.enabled(Feature.SLEEP_STATUS))
    }
    Text("Mi noche empieza a las $start:00", color = C.sub)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (18..23).forEach { h -> FilterChip(start == h, { start = h; vm.prefs.sleepStartHour = h; vm.refreshTracking() }, { Text("$h:00") }) }
    }
    Text("Mi hora habitual de despertar: ${if (wake > 12) wake - 12 else wake}:00 a. m.", color = C.sub)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (4..12).forEach { h -> FilterChip(wake == h, { wake = h; vm.prefs.wakeHour = h; vm.refreshTracking() }, { Text("$h:00") }) }
    }
    Text("Después de 30 minutos de inactividad nocturna: “Posiblemente durmiendo”. Una hora después de tu hora habitual: “Pasó su hora habitual de despertar”. El teléfono debe mantener Nosotros funcionando; apagarlo no cuenta como dormir.", color = C.sub, fontSize = 13.sp)
    if (appSharing || sleepSharing) Text("Estás compartiendo actividad. Puedes apagar ambos interruptores cuando quieras.", color = C.me)
    message?.let { Text(it, color = C.sub) }
    confirm?.let { feature ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("¿Compartir con tu pareja?") },
            text = { Text(if (feature == Feature.APP_PRESENCE) "Tu pareja verá el nombre de la última aplicación detectada en este teléfono. Es voluntario y puedes pausarlo aquí. No se comparten mensajes ni un historial de apps."
                else "Tu pareja verá una estimación de descanso e inactividad nocturna según tu horario. No es una medición de sueño. Puedes pausarla aquí.") },
            confirmButton = { TextButton(onClick = {
                val sleep = feature == Feature.SLEEP_STATUS
                if (sleep) sleepSharing = true else appSharing = true
                vm.setPresence(sleep, true); confirm = null
            }) { Text("Sí, compartir mi actividad") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } })
    }
}
