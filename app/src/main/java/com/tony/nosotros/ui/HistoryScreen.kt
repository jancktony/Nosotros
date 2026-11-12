package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tony.nosotros.data.Mode
import com.tony.nosotros.data.Trip
import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.TimeFmt

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val day by vm.historyDay.collectAsState()
    val places by vm.places.collectAsState()
    val trips by vm.dayTrips.collectAsState()
    val visits by vm.dayVisits.collectAsState()
    val uid by vm.uid.collectAsState()
    val partnerUid by vm.partnerUid.collectAsState()
    val me by vm.me.collectAsState()
    val partner by vm.partner.collectAsState()
    var showMe by remember { mutableStateOf(false) }
    var openTrip by remember { mutableStateOf<Trip?>(null) }

    val who = if (showMe) uid else partnerUid
    val accent = if (showMe) C.me else C.partner
    val items = buildTimeline(visits, trips, who)
    val whoTrips = trips.filter { it.uid == who }
    val totalKm = whoTrips.sumOf { it.distanceM }

    LazyColumn(Modifier.fillMaxSize().background(C.bg).statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
        item { Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsHero("Su recorrido", "Un día a la vez, sus lugares y sus momentos.", "LO QUE VIVIERON")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.historyDay.value = day - 1 }) { Icon(Ic.Left, "Día anterior", tint = C.text) }
                Text(TimeFmt.niceDayOffset(day), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { if (day < 0) vm.historyDay.value = day + 1 }, enabled = day < 0) {
                    Icon(Ic.Right, "Día siguiente", tint = if (day < 0) C.text else C.sub.copy(alpha = 0.3f))
                }
            }
            val personName = partner?.name?.ifBlank { null } ?: "Pareja"
            PanelTabs(listOf(personName, "Yo"), if (showMe) "Yo" else personName) { showMe = it == "Yo" }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Trayectos", "${whoTrips.size}", Modifier.weight(1f))
                Stat("Distancia", TimeFmt.km(totalKm), Modifier.weight(1f))
                Stat("Lugares", "${visits.count { it.uid == who }}", Modifier.weight(1f))
            }
        }
        }
            if (items.isEmpty()) item { SettingsHero("Un día tranquilo", "Aquí aparecerán sus recorridos y visitas cuando haya movimientos.", "SU HISTORIA") }
            items(items, key = { if (it is TimelineItem.T) "t" + it.t.id else "v" + (it as TimelineItem.V).v.id }) { item ->
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    TimelineRow(item, accent, onClick = if (item is TimelineItem.T) ({ openTrip = item.t }) else null)
                    if (item is TimelineItem.V) {
                        val visit = item.v
                        val place = places.firstOrNull { it.id == visit.placeId }
                        StopAddress(if (visit.lat != null && visit.lng != null) com.tony.nosotros.util.Pt(visit.lat,visit.lng)
                            else place?.let { com.tony.nosotros.util.Pt(it.lat,it.lng) })
                    }
                }
            }
    }

    openTrip?.let { t ->
        val current = trips.firstOrNull { it.id == t.id } ?: t
        TripDialog(current, accent, onMode = { vm.setTripMode(current.id, it) }, onClose = { openTrip = null })
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = C.sub, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripDialog(t: Trip, accent: androidx.compose.ui.graphics.Color, onMode: (Mode) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(C.bg)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val pts = Geo.decode(t.path)
                LiveMap(Modifier.fillMaxSize(), people = emptyList(),
                    routes = listOf(MapRoute(t.id, pts, accent.toArgb())), fitKey = t.id)
                Box(Modifier.statusBarsPadding().padding(16.dp).clip(RoundedCornerShape(50)).background(C.glass)) {
                    IconButton(onClick = onClose) { Icon(Ic.Close, "Cerrar", tint = C.text) }
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(C.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(t.mode.icon(), tint = accent)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(listOfNotNull(t.from, t.to).joinToString(" → ").ifEmpty { "Trayecto" },
                            style = MaterialTheme.typography.titleMedium)
                        Text("${TimeFmt.hour(t.start)}–${TimeFmt.hour(t.end)} · ${TimeFmt.km(t.distanceM)} · ${TimeFmt.duration(t.end - t.start)}",
                            color = C.sub, fontSize = 14.sp)
                    }
                }
                SectionLabel("¿Cómo fue este trayecto?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mode.correctable.forEach { m -> Chip(m.label, t.mode == m, accent, m.icon()) { onMode(m) } }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
