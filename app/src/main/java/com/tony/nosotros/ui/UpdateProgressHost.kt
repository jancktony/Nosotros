package com.tony.nosotros.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.nosotros.BuildConfig
import com.tony.nosotros.update.*

@Composable
fun UpdateProgressHost() {
    val context = LocalContext.current.applicationContext
    val state by InstallProgress.state.collectAsState()
    val expanded by InstallProgress.expanded.collectAsState()
    state?.let { snapshot ->
        UpdateProgressPanel(snapshot, expanded,
            onExpand = { InstallProgress.expanded.value = true },
            onMinimize = { InstallProgress.expanded.value = false },
            onCancel = { InstallProgress.cancel() }, onRetry = { InstallProgress.retry(context) },
            onConfirm = { InstallProgress.confirm(context) }, onDismiss = { InstallProgress.dismiss(context) })
    }
}

private fun InstallStage.label(): String = when (this) {
    InstallStage.CONNECTING -> "Preparando descarga"
    InstallStage.DOWNLOADING -> "Descargando actualización"
    InstallStage.VERIFYING -> "Verificando archivo"
    InstallStage.PREPARING -> "Preparando instalación"
    InstallStage.CONFIRMATION -> "Android necesita tu confirmación"
    InstallStage.INSTALLING -> "Instalando con Android"
    InstallStage.INSTALLED -> "Actualización instalada"
    InstallStage.FAILED -> "No se pudo completar"
    InstallStage.CANCELLED -> "Descarga cancelada"
}
private fun megabytes(bytes: Long) = String.format(java.util.Locale("es"), "%.1f MB", bytes / 1048576.0)

@Composable
internal fun UpdateProgressPanel(
    state: InstallSnapshot, expanded: Boolean,
    onExpand: () -> Unit, onMinimize: () -> Unit, onCancel: () -> Unit,
    onRetry: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    if (!expanded) {
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 76.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter) {
            Surface(shape = RoundedCornerShape(20.dp), color = C.surface, shadowElevation = 6.dp,
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().clickable(onClick = onExpand)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.stage.label(), color = C.text, fontWeight = FontWeight.SemiBold)
                    Text(state.percent?.let { "$it % · ${megabytes(state.bytes)} de ${megabytes(state.total)}" }
                        ?: "Nosotros ${state.version} · Ver detalles", color = C.sub, fontSize = 12.sp)
                    if (!state.terminal) InstallBar(state)
                }
            }
        }
        return
    }
    BackHandler(onBack = onMinimize)
    Surface(Modifier.fillMaxSize(), color = C.bg) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Actualización", color = C.text, fontWeight = FontWeight.Bold)
                TextButton(onClick = onMinimize) { Text("Minimizar", color = C.me) }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = C.me.copy(alpha = .14f)) {
                Text("N", Modifier.padding(horizontal = 26.dp, vertical = 16.dp), color = C.me, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            }
            Text("Nosotros", color = C.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Versión actual ${BuildConfig.VERSION_NAME} → ${state.version}", color = C.sub)
            Text(state.stage.label(), color = C.text, fontSize = 23.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(when (state.stage) {
                InstallStage.CONNECTING -> "Conectando con tu actualización privada…"
                InstallStage.DOWNLOADING -> "Puedes volver al mapa mientras se descarga."
                InstallStage.VERIFYING -> "Comprobando integridad, firma y versión del archivo."
                InstallStage.PREPARING -> "Copiando el archivo verificado al instalador."
                InstallStage.CONFIRMATION -> "Pulsa Continuar y autoriza la instalación en la pantalla de Android."
                InstallStage.INSTALLING -> "Android está procesando la actualización. Puede cerrar la app; al abrirla verás el resultado."
                InstallStage.INSTALLED -> "La versión ${state.version} ya está instalada en este teléfono."
                else -> state.message
            }, color = C.sub)
            if (state.stage == InstallStage.DOWNLOADING) {
                Text(state.percent?.let { "$it %" } ?: "Conectando…", color = C.me, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("${megabytes(state.bytes)} de ${megabytes(state.total)}", color = C.sub)
                if (state.bytesPerSecond > 0) Text(
                    String.format(java.util.Locale("es"), "%.0f KB/s", state.bytesPerSecond / 1024.0) + (state.secondsLeft?.let { " · ~${it.coerceAtLeast(1)} s restantes" } ?: ""),
                    color = C.sub, fontSize = 13.sp)
            }
            if (!state.terminal) InstallBar(state)
            Surface(shape = RoundedCornerShape(24.dp), color = C.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf("Descargar", "Verificar", "Instalar").forEachIndexed { index, title ->
                        val step = when (state.stage) {
                            InstallStage.CONNECTING, InstallStage.DOWNLOADING -> 0
                            InstallStage.VERIFYING -> 1
                            else -> 2
                        }
                        val done = state.stage == InstallStage.INSTALLED || (!state.terminal && index < step)
                        Text((if (done) "✓" else "${index + 1}") + "   " + title,
                            color = if (done || (!state.terminal && index == step)) C.me else C.sub)
                    }
                }
            }
            if (state.message.isNotBlank() && state.stage == InstallStage.CONFIRMATION) Text(state.message, color = C.sub)
            if (state.stage == InstallStage.CONFIRMATION) Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("Continuar instalación") }
            if (state.stage == InstallStage.FAILED || state.stage == InstallStage.CANCELLED)
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Volver a intentar") }
            if (state.cancellable) TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar descarga", color = C.sub) }
            if (state.terminal) Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Volver a Nosotros") }
        }
    }
}

@Composable
private fun InstallBar(state: InstallSnapshot) {
    if (state.percent != null) LinearProgressIndicator(progress = { state.percent!! / 100f }, modifier = Modifier.fillMaxWidth(), color = C.me)
    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = C.me)
}
