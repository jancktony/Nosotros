package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.tony.nosotros.data.*
import com.tony.nosotros.util.Pt
import com.tony.nosotros.util.TimeFmt
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

@Composable
fun MoreScreen(onSettings: () -> Unit, onScreen: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Más", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onSettings) { Icon(Ic.Gear, "Ajustes", tint = C.text) }
        }
        SettingsLink(Ic.Clock, "Historial", "Sus recorridos y paradas") { onScreen(1) }
        SettingsLink(Ic.Pin, "Lugares", "Sus puntos de encuentro") { onScreen(2) }
        SettingsLink(Ic.Layers, "Galería", "Sus recuerdos") { onScreen(3) }
    }
}

@Composable
fun FindScreen(vm: MainViewModel, onSettings: () -> Unit, onDirections: (Member) -> Unit) {
    val me by vm.me.collectAsState(); val partner by vm.partner.collectAsState()
    val info by vm.pairInfo.collectAsState(); val now by vm.tick.collectAsState()
    var own by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    val member = if (own) me else partner
    val scope = rememberCoroutineScope()
    val canRing = member?.let { it.findPhoneEnabled && FeatureAccess(info.featureFlags, info.admin == it.uid).enabled(Feature.FIND_PHONE) } == true
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Encontrar", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onSettings) { Icon(Ic.Gear, "Ajustes", tint = C.text) }
        }
        val people = member?.takeIf { it.hasLocation }?.let { listOf(MapPerson(it.uid, initialOf(it.name,"?"), C.me.toArgb(), C.meInk.toArgb(), it.lat,it.lng,it.mode,it.paused,it.speedKmh.toInt(),it.avatar)) }.orEmpty()
        Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 100.dp)) {
            LiveMap(Modifier.fillMaxSize(), people, fitKey = member?.uid,
                focus = member?.takeIf { it.hasLocation }?.let { listOf(Pt(it.lat,it.lng)) })
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 330.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val partnerName = partner?.name?.ifBlank { null } ?: "Pareja"
            PanelTabs(listOf(partnerName, "Yo"), if (own) "Yo" else partnerName) { own = it == "Yo"; message = null }
            Text(member?.name?.ifBlank { null } ?: "Esperando el teléfono", style = MaterialTheme.typography.titleLarge)
            member?.let { m ->
                Text("${if(m.paused) "Ubicación pausada" else if(m.hasLocation) "Última ubicación recibida" else "Sin ubicación"} · ${if(m.updatedAt > 0) TimeFmt.ago(m.updatedAt) else "sin señal"}", color = C.sub)
                if(m.battery >= 0) Text("Batería ${m.battery} %", color=C.sub)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { confirm = true }, enabled = canRing && !busy, modifier = Modifier.weight(1f)) { Text("Hacer sonar") }
                    OutlinedButton(onClick = { onDirections(m) }, enabled = m.hasLocation && !m.paused, modifier = Modifier.weight(1f)) { Text("Indicaciones") }
                }
                TextButton(enabled = !m.paused && !busy, onClick = {
                    val pair = vm.pairId.value ?: return@TextButton
                    scope.launch { busy=true; message=runCatching { Repo.requestLive(pair,m.uid,4); "Ubicación solicitada; esperando al teléfono" }.getOrElse { "No se pudo solicitar la ubicación" };busy=false }
                }) { Text("Solicitar ubicación reciente") }
            }
            message?.let { Text(it,color=C.me) }
            Text("El sonido requiere permiso previo en ese teléfono y respeta su volumen. Necesita conexión; no localiza teléfonos apagados.",color=C.sub,style=MaterialTheme.typography.bodySmall)
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("¿Hacer sonar el teléfono?")},text={Text("Sonará hasta 30 segundos según su volumen. La persona puede detenerlo desde su teléfono.")},
        confirmButton={TextButton(onClick={confirm=false;val target=member?.uid;val pair=vm.pairId.value;val uid=vm.uid.value
            if(target!=null&&pair!=null&&uid!=null)scope.launch{busy=true;message=runCatching{
                Repo.db.collection("pairs").document(pair).collection("deviceCommands").document(target).set(mapOf("requestId" to UUID.randomUUID().toString(),"requester" to uid,"type" to "ring","status" to "queued","createdAt" to FieldValue.serverTimestamp(),"expiresAt" to System.currentTimeMillis()+59000)).await()
                "Solicitud enviada. Si no suena, revisa conexión, permiso y volumen."
            }.getOrElse{"No se pudo enviar. Espera unos segundos y revisa el permiso del teléfono."};busy=false}
        }){Text("Hacer sonar")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})
}
