package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.nosotros.BuildConfig
import com.tony.nosotros.data.Feature
import com.tony.nosotros.update.Updater
import com.tony.nosotros.util.TimeFmt
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(vm: MainViewModel, onBack: () -> Unit) {
    val allowed by vm.isAdmin.collectAsState()
    if (!allowed) { TextButton(onClick = onBack) { Text("Solo el administrador puede entrar. Volver") }; return }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val message by vm.adminMessage.collectAsState()
    val partner by vm.partner.collectAsState()
    val info by vm.pairInfo.collectAsState()
    val reviews by vm.releaseReviews.collectAsState()
    val busy by vm.releaseBusy.collectAsState()
    val featureBusy by vm.featureBusy.collectAsState()
    val code by vm.newCode.collectAsState()
    var tab by remember { mutableStateOf("Resumen") }
    var brand by remember(info.creatorBrand) { mutableStateOf(info.creatorBrand) }
    var search by remember { mutableStateOf("") }
    var releases by remember { mutableStateOf<List<Updater.Release>?>(null) }
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var force by remember { mutableStateOf<Updater.Release?>(null) }
    var mobile by remember { mutableStateOf(false) }
    var requests by remember { mutableStateOf(false) }
    if(requests) { RequestsScreen(vm) { requests=false }; return }
    var corrections by remember { mutableStateOf<Updater.Release?>(null) }
    var notes by remember { mutableStateOf("") }
    val name = partner?.name?.ifBlank { null } ?: "tu pareja"
    LaunchedEffect(Unit) { releases = runCatching { Updater.releases() }.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Ic.Left, "Volver", tint = C.text) }
            Text("Administración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = C.text)
        }
        SettingsHero("Tú decides", "Primero pruebas. Después compartes con $name.", "CREADO POR TI")
        PanelTabs(listOf("Resumen", "Versiones", "Funciones", "Herramientas"), tab) { tab = it }
        message?.let { Text(it, color = C.me, fontSize = 13.sp) }
        if (tab == "Resumen") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WidgetPreview("Tu teléfono", BuildConfig.VERSION_NAME, C.me, Modifier.weight(1f))
                WidgetPreview("Para $name", info.relVersion ?: "Sin liberar", C.partner, Modifier.weight(1f))
            }
            SettingsLink(Ic.Clock, "Versiones", "Probar, aprobar, liberar o forzar") { tab = "Versiones" }
            SettingsLink(Ic.Sparkle, "Funciones", "Apagadas, solo para ti o para ambos") { tab = "Funciones" }
            SettingsLink(Ic.Layers, "Panel web privado", "Tus herramientas desde cualquier dispositivo") { WebPanel.open(ctx) }
            Section(Ic.User, "Teléfono de $name") {
                AdminLine("Instalada", partner?.appVersion?.ifBlank { null } ?: "Sin datos")
                AdminLine("Última señal", partner?.updatedAt?.takeIf { it > 0 }?.let { TimeFmt.ago(it) } ?: "Sin señal")
                AdminLine("Batería", partner?.battery?.takeIf { it >= 0 }?.let { "$it %" } ?: "—")
                AdminLine("Ubicación", if (partner?.paused == true) "En pausa" else "Según permisos")
                val status = when(partner?.updateStatus) {
                    "downloading" -> "Descargando"; "installing" -> "Instalación enviada a Android"
                    "confirmation" -> "Necesita confirmar en el teléfono"; "failed" -> "No se pudo instalar; puedes reintentar"
                    "installed" -> "Instalada"; else -> "Sin solicitud reciente"
                }
                AdminLine("Actualización", status)
            }
        }
        if (tab == "Funciones") {
            OutlinedTextField(search, { search = it }, label = { Text("Buscar una función") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Habilitar una función no concede permisos ni acepta el consentimiento por la otra persona.", color = C.sub, fontSize = 12.sp)
            val groups = listOf(
                "Voz y conexión" to listOf(Feature.WALKIE, Feature.WALKIE_RELAY, Feature.VOICE_NOTES),
                "Mapa y presencia" to listOf(Feature.MAP_3D, Feature.MAP_GLOBE, Feature.MAP_PLACES, Feature.APP_PRESENCE, Feature.SLEEP_STATUS, Feature.FIND_PHONE),
                "Su espacio" to listOf(Feature.PROFILE_PHOTO, Feature.COMPACT_WIDGETS, Feature.PLACE_ALERTS, Feature.LOCK_DETAILS, Feature.MANUAL_UPDATES, Feature.AUTO_UPDATES))
            groups.forEach { (title, features) ->
                val matches = features.filter { it.title.contains(search, true) }
                if (matches.isNotEmpty()) Section(Ic.Sparkle, title) {
                    matches.forEachIndexed { index, feature ->
                        if (index > 0) Spacer(Modifier.height(6.dp))
                        Text(feature.title, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        FeatureModes(info.featureFlags[feature.id] ?: feature.defaultMode, !featureBusy) { vm.setFeature(feature, it) }
                    }
                }
            }
        }
        if (tab == "Versiones") {
            Text("Tu prueba es privada. Solo se instala en $name la versión que apruebes y liberes.", color = C.sub, fontSize = 13.sp)
            TextButton(onClick = { scope.launch { releases = null; releases = runCatching { Updater.releases() }.getOrDefault(emptyList()) } }) {
                Icon(Ic.Sparkle, null, Modifier.size(15.dp)); Spacer(Modifier.width(8.dp)); Text("Recargar versiones")
            }
            if (releases == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (releases!!.isEmpty()) Text("No se pudieron leer candidatas. Revisa la conexión.", color = C.sub)
            releases.orEmpty().forEach { release ->
                val installed = release.version == BuildConfig.VERSION_NAME
                val approved = reviews[release.version]?.state == "approved"
                val live = info.relVersion == release.version
                Section(Ic.Layers, "Versión ${release.version}") {
                    Text(when { live -> "LIBERADA PARA $name"; approved -> "TU PRUEBA ESTÁ APROBADA"; installed -> "INSTALADA EN TU TELÉFONO"; else -> "CANDIDATA SIN PROBAR" },
                        color = if (approved || live) C.me else C.sub, fontSize = 10.sp, letterSpacing = 1.sp)
                    if (release.notes.isNotBlank()) Text(release.notes, maxLines = 3, fontSize = 12.sp, color = C.sub)
                    reviews[release.version]?.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.warn, fontSize = 12.sp) }
                    if (!installed && Updater.newer(release.version, BuildConfig.VERSION_NAME))
                        PrimaryAction("Instalar solo para mí", !busy) { vm.testRelease(release) }
                    if (installed && !approved) PrimaryAction("Aprobar mi prueba", !busy) { vm.reviewRelease(release.version, "approved") }
                    if (approved && installed) {
                        if (!live) PrimaryAction("Liberar para $name", !busy) {
                            confirmation = "${release.version} quedará disponible para $name. Su información se conserva." to { vm.release(release) }
                        }
                        OutlinedButton(onClick = { mobile = false; force = release }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Forzar actualización") }
                        Text("Orden puntual: solo esta versión aprobada. Android puede pedir confirmación. El teléfono necesita una versión compatible.", fontSize = 11.sp, color = C.sub)
                    }
                    TextButton(onClick = { corrections = release; notes = reviews[release.version]?.notes.orEmpty() }, enabled = !busy) { Text("Pedir correcciones", color = C.sub) }
                }
            }
        }
        if (tab == "Herramientas") {
            SettingsLink(Ic.Clock, "Mis solicitudes", "Correcciones e ideas · solo para ti") { requests = true }
            SettingsLink(Ic.Layers, "Abrir panel web", WebPanel.URL) { WebPanel.open(ctx) }
            ActionButton("Copiar enlace de la web", C.sub) {
                ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Nosotros", WebPanel.URL))
            }
            Section(Ic.User, "Tu marca") {
                OutlinedTextField(brand, { brand = it.take(60) }, label = { Text("Creador de Nosotros") }, modifier = Modifier.fillMaxWidth())
                PrimaryAction("Guardar mi marca") { vm.setCreatorBrand(brand) }
            }
            Section(Ic.Locate, "Encontrar teléfono") {
                ActionButton("Solicitar ubicación reciente", C.me) { vm.requestLive() }
                Text("La pausa y los permisos del teléfono se respetan. Para hacerlo sonar, abre el panel web; debe estar permitido en ese teléfono.", color = C.sub, fontSize = 12.sp)
            }
            Section(Ic.Link, "Su conexión") {
                code?.let { Text(it, color = C.me, fontSize = 26.sp, letterSpacing = 3.sp) }
                ActionButton("Cambiar teléfono vinculado", C.warn) { confirmation = "El teléfono anterior de $name se desvinculará hasta introducir el código nuevo." to { vm.resetPartner() } }
                ActionButton("Ceder administración", C.danger) { confirmation = "Solo $name podrá devolverte la administración." to { vm.giveAdmin(); onBack() } }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    force?.let { release -> AlertDialog(onDismissRequest = { force = null }, title = { Text("Forzar ${release.version}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Se liberará esta versión aprobada y se solicitará instalarla aunque las actualizaciones automáticas personales estén apagadas. Wi-Fi por defecto; Android puede pedir confirmar.")
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(mobile, { mobile = it }); Text("Permitir datos móviles en esta orden", fontSize = 13.sp) }
        } }, confirmButton = { TextButton(onClick = { vm.release(release, true, mobile); force = null }) { Text("Enviar solicitud") } },
        dismissButton = { TextButton(onClick = { force = null }) { Text("Cancelar") } }) }
    corrections?.let { release -> AlertDialog(onDismissRequest = { corrections = null }, title = { Text("Tus correcciones") },
        text = { OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Qué quieres cambiar") }) },
        confirmButton = { TextButton(enabled = notes.isNotBlank(), onClick = { vm.reviewRelease(release.version, "changes_requested", notes.trim()); corrections = null }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { corrections = null }) { Text("Cancelar") } }) }
    confirmation?.let { (text, action) -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("¿Continuar?") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { action(); confirmation = null }) { Text("Sí, continuar") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } }) }
}

@Composable
private fun AdminLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, color = C.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.text, fontSize = 12.sp, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
    }
}
