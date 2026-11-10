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
import androidx.compose.material3.OutlinedTextField
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
    val reviews by vm.releaseReviews.collectAsState()
    val releaseBusy by vm.releaseBusy.collectAsState()
    var corrections by remember { mutableStateOf<String?>(null) }
    var correctionText by remember { mutableStateOf("") }
    var creatorBrand by remember(info.creatorBrand) { mutableStateOf(info.creatorBrand) }
    val newCode by vm.newCode.collectAsState()
    val pName = partner?.name?.ifBlank { null } ?: "tu pareja"
    var releases by remember { mutableStateOf<List<Updater.Release>?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LaunchedEffect(Unit) { releases = runCatching { Updater.releases() }.getOrDefault(emptyList()) }

    corrections?.let { version ->
        AlertDialog(onDismissRequest = { corrections = null }, title = { Text("Qué debemos corregir en $version") },
            text = { OutlinedTextField(correctionText, { correctionText = it.take(2000) }, label = { Text("Tus observaciones") }) },
            confirmButton = { TextButton(enabled = correctionText.trim().isNotEmpty() && !releaseBusy, onClick = {
                vm.reviewRelease(version, "changes_requested", correctionText.trim()); corrections = null
            }) { Text("Guardar correcciones") } },
            dismissButton = { TextButton(onClick = { corrections = null }) { Text("Cancelar") } })
    }
    Column(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Left, "Volver", tint = C.text) }
            Text("Administrador", style = MaterialTheme.typography.headlineMedium)
        }

        Section(Ic.User, "Mi marca de creador") {
            OutlinedTextField(creatorBrand, { creatorBrand = it.take(60) }, label = { Text("Nombre o marca") }, modifier = Modifier.fillMaxWidth())
            Text("Aparecerá como creador en la app y la web.", color = C.sub, fontSize = 13.sp)
            ActionButton("Guardar mi marca", C.me) { vm.setCreatorBrand(creatorBrand) }
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
            Text("Primero instalas solo en tu teléfono. Después decides si pedir correcciones o aprobar la prueba. Solo entonces liberas para $pName. Ninguna prueba actualiza su teléfono.",
                color = C.sub, fontSize = 14.sp)
            Line("Tu versión", BuildConfig.VERSION_NAME)
            Line("Versión de $pName", partner?.appVersion?.ifBlank { null } ?: "desconocida")
            Line("Liberada para $pName", info.relVersion ?: "Ninguna: requiere tu aprobación")
            ActionButton("Recargar versiones", C.sub) { scope.launch { releases = null; releases = runCatching { Updater.releases() }.getOrDefault(emptyList()) } }
            adminMessage?.let { Text(it, color = C.me, fontSize = 13.sp) }
            Divider()
            when {
                releases == null -> Text("Cargando versiones…", color = C.sub, fontSize = 14.sp)
                releases!!.isEmpty() -> Text("No pude leer las versiones. Revisa tu internet.", color = C.sub, fontSize = 14.sp)
                else -> releases!!.forEach { r ->
                    val live = info.relVersion == r.version
                    val installed = r.version == BuildConfig.VERSION_NAME
                    val review = reviews[r.version]
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("Versión ${r.version}", fontWeight = FontWeight.Bold, color = C.text)
                        Text(r.notes.lines().firstOrNull { it.isNotBlank() }.orEmpty(), color = C.sub, fontSize = 13.sp, maxLines = 2)
                        Text(when { live -> "Liberada para tu pareja"; review?.state == "approved" -> "Tu prueba está aprobada";
                            review?.state == "changes_requested" -> "Correcciones pendientes"; installed -> "Instalada solo en tu teléfono";
                            info.testVersion == r.version -> "Seleccionada para tu prueba"; else -> "Candidata sin probar" },
                            color = if (live || review?.state == "approved") C.ok else C.sub, fontSize = 13.sp)
                        if (review?.notes?.isNotBlank() == true) Text(review.notes, color = C.sub, fontSize = 13.sp)
                        if (!installed && Updater.newer(r.version, BuildConfig.VERSION_NAME))
                            TextButton(enabled = !releaseBusy, onClick = { vm.testRelease(r) }) { Text("Instalar solo para mí", color = C.me) }
                        if (!live) Row {
                            TextButton(enabled = !releaseBusy, onClick = { corrections = r.version; correctionText = review?.notes.orEmpty() }) { Text("Pedir correcciones", color = C.warn) }
                            if (installed && review?.state != "approved") TextButton(enabled = !releaseBusy, onClick = { vm.reviewRelease(r.version, "approved") }) { Text("Aprobar mi prueba", color = C.me) }
                        }
                        if (!live && review?.state == "approved" && installed) TextButton(enabled = !releaseBusy, onClick = {
                            confirm = "Tu pareja podrá actualizar a ${r.version}. Tu prueba ya está aprobada." to { vm.release(r) }
                        }) { Text("Liberar para mi pareja", color = C.me, fontWeight = FontWeight.Bold) }
                        Divider()
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
                    "Actualizaciones: ${info.relVersion ?: "pendiente de aprobación"}"
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
