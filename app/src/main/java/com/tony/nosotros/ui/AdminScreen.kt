package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.tony.nosotros.BuildConfig
import com.tony.nosotros.update.Updater
import com.tony.nosotros.util.TimeFmt

/** Panel del administrador: versiones, estado del otro teléfono y vinculación. */
@Composable
fun AdminScreen(vm: MainViewModel, onBack: () -> Unit) {
    val allowed by vm.isAdmin.collectAsState()
    if (!allowed) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Solo el administrador puede abrir este panel.", color = C.sub)
            TextButton(onClick = onBack) { Text("Volver", color = C.me) }
        }
        return
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val adminMessage by vm.adminMessage.collectAsState()
    val partner by vm.partner.collectAsState()
    val info by vm.pairInfo.collectAsState()
    val newCode by vm.newCode.collectAsState()
    val pName = partner?.name?.ifBlank { null } ?: "tu pareja"
    var releases by remember { mutableStateOf<List<Updater.Release>?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LaunchedEffect(Unit) { releases = runCatching { Updater.releases() }.getOrDefault(emptyList()) }

    Column(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Left, "Volver", tint = C.text) }
            Text("Administrador", style = MaterialTheme.typography.headlineMedium)
        }

        Section(Ic.Sparkle, "Funciones") {
            Text("Desactivar, probar solo en tu cuenta o liberar para ambos. Tu pareja necesita una versión compatible y conexión para recibir los cambios.", color = C.sub, fontSize = 13.sp)
            val busy by vm.featureBusy.collectAsState()
            com.tony.nosotros.data.Feature.entries.forEach { feature ->
                Text(feature.title, color = C.text, fontWeight = FontWeight.Bold)
                val mode = info.featureFlags[feature.id] ?: feature.defaultMode
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("off" to "Desactivada", "admin" to "Solo yo", "all" to "Ambos").forEach { (value, label) ->
                        TextButton(enabled = !busy && mode != value, onClick = { vm.setFeature(feature, value) }) {
                            Text(if (mode == value) "✓ $label" else label, color = if (mode == value) C.ok else C.me, fontSize = 12.sp)
                        }
                    }
                }
                Divider()
            }
            adminMessage?.let { Text(it, color = C.me, fontSize = 13.sp) }
        }

        Section(Ic.Sparkle, "Versiones") {
            Text("Puedes fijar la versión de $pName o permitir que reciba automáticamente la última. La instalación respeta sus ajustes y la confirmación de Android.",
                color = C.sub, fontSize = 14.sp)
            Line("Tu versión", BuildConfig.VERSION_NAME)
            Line("Versión de $pName", partner?.appVersion?.ifBlank { null } ?: "desconocida")
            Line("Liberada para $pName", info.relVersion ?: "Automático: última versión")
            if (info.relVersion != null) ActionButton("Activar versiones automáticas para mi pareja", C.me) { vm.allowAutomaticPartnerUpdates() }
            ActionButton("Recargar versiones", C.sub) { scope.launch { releases = null; releases = runCatching { Updater.releases() }.getOrDefault(emptyList()) } }
            adminMessage?.let { Text(it, color = C.me, fontSize = 13.sp) }
            Divider()
            when {
                releases == null -> Text("Cargando versiones…", color = C.sub, fontSize = 14.sp)
                releases!!.isEmpty() -> Text("No pude leer las versiones. Revisa tu internet.", color = C.sub, fontSize = 14.sp)
                else -> releases!!.forEach { r ->
                    val live = info.relVersion == r.version
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Versión ${r.version}", fontWeight = FontWeight.Bold, color = C.text)
                            Text(listOf(r.date, r.notes.lines().firstOrNull { it.isNotBlank() } ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                                color = C.sub, fontSize = 13.sp, maxLines = 2)
                        }
                        if (live) Text("Liberada", color = C.ok, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        else TextButton(onClick = { vm.release(r) }) { Text("Liberar", color = C.me, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Section(Ic.User, "Teléfono de $pName") {
            val p = partner
            if (p == null) Text("Todavía no se ha vinculado.", color = C.sub, fontSize = 14.sp) else {
                Line("Última señal", if (p.updatedAt > 0) TimeFmt.ago(p.updatedAt) else "nunca")
                Line("Batería", if (p.battery >= 0) "${p.battery}%${if (p.charging) " (cargando)" else ""}" else "—")
                Line("Precisión", if (p.acc > 0) "±${p.acc.toInt()} m" else "—")
                Line("Ahorro extremo", if (p.saving) "activo" else "no")
                Line("Ubicación", if (p.paused) "en pausa" else "compartiendo")
            }
        }

        Section(Ic.Locate, "Herramientas del administrador") {
            ActionButton("Solicitar ubicación reciente", C.me) {
                vm.requestLive()
            }
            Text("No cambia los permisos ni activa la ubicación si tu pareja la pausó.", color = C.sub, fontSize = 13.sp)
            ActionButton("Compartir diagnóstico", C.sub) {
                val report = "Nosotros ${BuildConfig.VERSION_NAME}\n" +
                    "Versión de la pareja: ${partner?.appVersion ?: "sin datos"}\n" +
                    "Última señal: ${partner?.updatedAt?.let { TimeFmt.ago(it) } ?: "sin datos"}\n" +
                    "Batería de la pareja: ${partner?.battery ?: -1}%\n" +
                    "Ubicación en pausa: ${partner?.paused == true}\n" +
                    "Actualizaciones: ${info.relVersion ?: "automáticas"}"
                ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, report), "Compartir diagnóstico"))
            }
        }

        Section(Ic.Pin, "Lugares") {
            Text("En la pestaña Lugares puedes editar y borrar también los lugares de $pName.", color = C.sub, fontSize = 14.sp)
        }

        Section(Ic.Layers, "Panel web") {
            Text("El panel web requiere tu mismo correo verificado y contraseña. Ya no se admiten navegadores anónimos.", color = C.sub, fontSize = 14.sp)
        }

        Section(Ic.Link, "Vinculación") {
            Text("Si $pName cambia de teléfono, genera un código nuevo. El teléfono anterior deja de ver la ubicación.",
                color = C.sub, fontSize = 14.sp)
            newCode?.let { Text("Código nuevo: $it  (dura 24 h)", color = C.me, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            ActionButton("Cambiar el teléfono vinculado", C.warn) {
                confirm = "El teléfono actual de $pName quedará desvinculado hasta que escriba el código nuevo." to { vm.resetPartner() }
            }
            ActionButton("Ceder la administración a $pName", C.danger) {
                confirm = "Dejarás de ser administrador y solo $pName podrá devolvértelo." to { vm.giveAdmin(); onBack() }
            }
        }
    }

    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = C.surface,
            title = { Text("¿Seguro?") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Sí, continuar", color = C.danger) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar", color = C.sub) } },
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = C.sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
