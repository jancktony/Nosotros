package com.tony.nosotros.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.tony.nosotros.BuildConfig
import com.tony.nosotros.update.Updater
import kotlinx.coroutines.launch

@Composable
fun UpdateControls(pairId: String?) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<Updater.Release?>(null) }
    Text("Versión instalada: ${BuildConfig.VERSION_NAME}", color = C.sub, fontSize = 13.sp)
    TextButton(enabled = !busy, onClick = {
        busy = true
        message = "Buscando actualizaciones…"
        scope.launch {
            runCatching { Updater.target(pairId) }
                .onSuccess { release ->
                    available = release
                    message = if (release == null) "Tienes la última versión disponible para ti." else "Nueva versión ${release.version} disponible."
                }
                .onFailure { message = "No se pudo comprobar. Revisa tu conexión y vuelve a intentarlo." }
            busy = false
        }
    }) { Text(if (busy) "Un momento…" else "Buscar actualizaciones", color = C.me) }
    message?.let { Text(it, color = C.sub, fontSize = 13.sp) }
    available?.let { release ->
        AlertDialog(
            onDismissRequest = { if (!busy) available = null }, containerColor = C.surface,
            title = { Text("Actualizar a ${release.version}") },
            text = { Text(if (busy) "Descargando y comprobando la actualización…" else release.notes.ifBlank { "Hay una nueva versión disponible." }) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val ok = Updater.install(ctx, release)
                    message = if (ok) "Actualización enviada a Android. Confirma la instalación si te lo pide." else "No se pudo descargar o validar. Puedes volver a intentarlo."
                    busy = false
                    available = null
                }
            }) { Text("Actualizar ahora", color = C.me) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { available = null }) { Text("Luego", color = C.sub) } }
        )
    }
}
