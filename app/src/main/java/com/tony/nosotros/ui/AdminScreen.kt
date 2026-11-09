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
import com.tony.nosotros.BuildConfig
import com.tony.nosotros.update.Updater
import com.tony.nosotros.util.TimeFmt

/** Panel del administrador: versiones, estado del otro teléfono y vinculación. */
@Composable
fun AdminScreen(vm: MainViewModel, onBack: () -> Unit) {
    val partner by vm.partner.collectAsState()
    val info by vm.pairInfo.collectAsState()
    val newCode by vm.newCode.collectAsState()
    val pName = partner?.name?.ifBlank { null } ?: "tu pareja"
    var releases by remember { mutableStateOf<List<Updater.Release>?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LaunchedEffect(Unit) { releases = Updater.releases() }

    Column(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Left, "Volver", tint = C.text) }
            Text("Administrador", style = MaterialTheme.typography.headlineMedium)
        }

        Section(Ic.Sparkle, "Versiones") {
            Text("Cada versión nueva se sube sola aquí. Tú la recibes primero para probarla; $pName solo recibe la que tú liberes.",
                color = C.sub, fontSize = 14.sp)
            Line("Tu versión", BuildConfig.VERSION_NAME)
            Line("Versión de $pName", partner?.appVersion?.ifBlank { null } ?: "desconocida")
            Line("Liberada para $pName", info.relVersion ?: "la última (sin control)")
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

        Section(Ic.Pin, "Lugares") {
            Text("En la pestaña Lugares puedes editar y borrar también los lugares de $pName.", color = C.sub, fontSize = 14.sp)
        }

        Section(Ic.Layers, "Panel web") {
            val webMsg by vm.webMsg.collectAsState()
            var code by remember { mutableStateOf("") }
            Text("Abre tu panel web en el computador y escribe aquí el código que te muestra.", color = C.sub, fontSize = 14.sp)
            androidx.compose.material3.OutlinedTextField(code, { code = it.uppercase().take(8) }, singleLine = true,
                label = { Text("Código de 8 letras") }, modifier = Modifier.fillMaxWidth())
            ActionButton("Dar acceso a ese navegador", C.me) { if (code.length == 8) { vm.linkWeb(code); code = "" } }
            ActionButton("Cerrar el acceso web", C.danger) { vm.unlinkWeb() }
            webMsg?.let { Text(it, color = C.text, fontSize = 14.sp) }
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
