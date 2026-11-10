package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.nosotros.data.Mode
import com.tony.nosotros.data.Routing
import com.tony.nosotros.util.Pt
import com.tony.nosotros.util.TimeFmt
import kotlinx.coroutines.launch

/** Buscar una dirección y ver la ruta paso a paso, a pie o en vehículo. */
@Composable
fun NavScreen(vm: MainViewModel, onClose: () -> Unit) {
    val me by vm.me.collectAsState()
    val partner by vm.partner.collectAsState()
    val satellite by vm.satellite.collectAsState()
    val threeD by vm.map3d.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalFocusManager.current
    val density = LocalDensity.current

    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Routing.Hit>?>(null) }
    var dest by remember { mutableStateOf<Routing.Hit?>(null) }
    var foot by remember { mutableStateOf(true) }
    var route by remember { mutableStateOf<Routing.Route?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fitKey by remember { mutableIntStateOf(0) }

    val myPt = me?.takeIf { it.hasLocation }?.let { Pt(it.lat, it.lng) }

    fun search() {
        keyboard.clearFocus()
        scope.launch {
            busy = true; error = null
            hits = Routing.search(query, myPt)
            if (hits.isNullOrEmpty()) error = "No encontré esa dirección. Prueba con barrio o ciudad."
            busy = false
        }
    }

    LaunchedEffect(dest, foot) {
        val d = dest ?: return@LaunchedEffect
        val from = myPt
        if (from == null) { error = "Aún no tengo tu ubicación."; return@LaunchedEffect }
        busy = true; error = null; route = null
        route = Routing.route(from, Pt(d.lat, d.lng), foot)
        if (route == null) error = "No pude calcular la ruta. Revisa tu internet."
        busy = false; fitKey++
    }

    val people = buildList {
        me?.takeIf { it.hasLocation }?.let {
            add(MapPerson(it.uid, initialOf(it.name, "Yo"), C.me.toArgb(), C.meInk.toArgb(), it.lat, it.lng, it.mode, it.paused, it.speedKmh.toInt(), it.avatar))
        }
        partner?.takeIf { it.hasLocation }?.let {
            add(MapPerson(it.uid, initialOf(it.name, "P"), C.partner.toArgb(), C.partnerInk.toArgb(), it.lat, it.lng, it.mode, it.paused, it.speedKmh.toInt(), it.avatar))
        }
    }
    val r = route

    Box(Modifier.fillMaxSize().background(C.bg)) {
        LiveMap(
            modifier = Modifier.fillMaxSize(),
            people = people,
            routes = r?.let { listOf(MapRoute("nav", it.points, C.me.toArgb())) } ?: emptyList(),
            fitKey = fitKey,
            focus = r?.points ?: myPt?.let { listOf(it) },
            satellite = satellite, threeD = threeD,
            topInsetPx = with(density) { 150.dp.roundToPx() },
            bottomInsetPx = with(density) { (if (r != null) 300 else 0).dp.roundToPx() },
            onLongPress = { lat, lng -> dest = Routing.Hit("Punto elegido en el mapa", lat, lng); hits = null },
        )

        Column(Modifier.statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Ic.Left, "Volver", tint = C.text) }
                OutlinedTextField(
                    query, { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("¿A dónde vas?", color = C.sub) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                IconButton(onClick = { search() }, enabled = query.isNotBlank()) { Icon(Ic.Nav, "Buscar", tint = C.me) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("A pie", foot, icon = Ic.Walk) { foot = true }
                Chip("Carro / moto", !foot, icon = Ic.Car) { foot = false }
                partner?.takeIf { it.hasLocation && !it.paused }?.let { p ->
                    Chip("Ir donde ${p.name.ifBlank { "mi pareja" }}", false, C.partner, Ic.Heart) {
                        dest = Routing.Hit(p.name.ifBlank { "Tu pareja" }, p.lat, p.lng); hits = null
                    }
                }
            }
            if (busy) Note("Buscando…")
            error?.let { Note(it) }
            hits?.takeIf { it.isNotEmpty() }?.let { list ->
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(C.surface)) {
                    list.forEach { h ->
                        Row(
                            Modifier.fillMaxWidth().clickable { dest = h; hits = null }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Ic.Pin, null, tint = C.me, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.name, color = C.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                myPt?.let { Text("A ${TimeFmt.km(com.tony.nosotros.util.Geo.dist(it.lat, it.lng, h.lat, h.lng))}", color = C.sub, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
            if (dest == null && hits == null && !busy)
                Note("Escribe una dirección, o mantén presionado el mapa para elegir un punto.")
        }

        if (r != null) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(C.surface).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (foot) Ic.Walk else Mode.CAR.icon(), null, tint = C.me, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${minutes(r.seconds)} · ${TimeFmt.km(r.distanceM)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.text)
                    Text(dest?.name ?: "", color = C.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { dest = null; route = null }) { Icon(Ic.Close, "Quitar ruta", tint = C.sub) }
            }
            if (foot) Text("Ruta por calles principales cuando es posible. Mantente en zonas iluminadas y con gente.",
                color = C.warn, fontSize = 12.sp)
            LazyColumn(Modifier.heightIn(max = 190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(r.steps) { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = C.me, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(26.dp))
                        Text(s.text, color = C.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (s.distanceM > 0) Text(TimeFmt.km(s.distanceM), color = C.sub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = C.text, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.glass).padding(horizontal = 12.dp, vertical = 8.dp))
}

private fun minutes(seconds: Double): String {
    val m = (seconds / 60).toInt().coerceAtLeast(1)
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}
