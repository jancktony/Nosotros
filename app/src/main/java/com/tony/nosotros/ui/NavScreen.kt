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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.tony.nosotros.util.RouteProgress
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavScreen(vm: MainViewModel, initialDestination: Routing.Hit? = null, onClose: () -> Unit) {
    val me by vm.me.collectAsState()
    val partner by vm.partner.collectAsState()
    val satellite by vm.satellite.collectAsState()
    val threeD by vm.map3d.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalFocusManager.current
    val density = LocalDensity.current

    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Routing.Hit>?>(null) }
    var dest by remember(initialDestination) { mutableStateOf(initialDestination) }
    var foot by remember { mutableStateOf(true) }
    var route by remember { mutableStateOf<Routing.Route?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var navigating by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    var along by remember { mutableStateOf(0.0) }
    var offRoute by remember { mutableStateOf(false) }
    var arrived by remember { mutableStateOf(false) }
    var fitKey by remember { mutableIntStateOf(0) }

    val localFix = navigationLocation(navigating)
    val sharedPt = me?.takeIf { it.hasLocation }?.let { Pt(it.lat, it.lng) }

    val myPt = localFix?.let { Pt(it.latitude,it.longitude) } ?: sharedPt

    fun search() {
        keyboard.clearFocus()
        scope.launch {
            busy = true; error = null
            hits = Routing.search(query, myPt)
            if (hits.isNullOrEmpty()) error = "No encontré esa dirección. Prueba con barrio o ciudad."
            busy = false
        }
    }

    LaunchedEffect(dest, foot, myPt != null) {
        val d = dest ?: return@LaunchedEffect
        val from = myPt
        if (from == null) { error = "Aún no tengo tu ubicación."; return@LaunchedEffect }
        navigating = false; along = 0.0; arrived = false; busy = true; error = null; route = null
        route = Routing.route(from, Pt(d.lat, d.lng), foot)
        if (route == null) error = "No pude calcular la ruta. Revisa tu internet."
        busy = false; fitKey++
    }

    val people = buildList {
        me?.takeIf { it.hasLocation }?.let {
            add(MapPerson(it.uid, initialOf(it.name, "Yo"), C.me.toArgb(), C.meInk.toArgb(), (localFix?.latitude ?: it.lat), (localFix?.longitude ?: it.lng), it.mode, it.paused, it.speedKmh.toInt(), it.avatar))
        }
        partner?.takeIf { it.hasLocation }?.let {
            add(MapPerson(it.uid, initialOf(it.name, "P"), C.partner.toArgb(), C.partnerInk.toArgb(), it.lat, it.lng, it.mode, it.paused, it.speedKmh.toInt(), it.avatar))
        }
    }
    val r = route

    val now by vm.tick.collectAsState()
    val fresh = localFix?.let { now-it.time in 0..15_000 && it.accuracy<=80 } ?: me?.let { !it.paused && it.updatedAt > 0 && now-it.updatedAt in 0..60_000 && it.acc <= 80 } == true
    LaunchedEffect(me?.updatedAt, localFix?.time, navigating, route) {
        val current = route ?: return@LaunchedEffect
        val point = myPt ?: return@LaunchedEffect
        if(!navigating || !fresh)return@LaunchedEffect
        val fix = RouteProgress.locate(current.points,point) ?: return@LaunchedEffect
        offRoute = fix.awayM > 80
        if(!offRoute){
            along = maxOf(along,fix.alongM)
            val end=current.points.lastOrNull()
            arrived = end!=null && com.tony.nosotros.util.Geo.dist(point.lat,point.lng,end.lat,end.lng)<25 && along>=current.distanceM-60
            if(follow)fitKey++
        }
    }
    val nextIndex = r?.let { RouteProgress.nextStep(it.steps.map { step -> step.distanceM },along) } ?: 0
    BoxWithConstraints(Modifier.fillMaxSize().background(C.bg)) {
        val short = maxHeight < 500.dp
        LiveMap(
            modifier = Modifier.fillMaxSize(),
            people = people,
            routes = r?.let { listOf(MapRoute("nav", it.points, C.me.toArgb())) } ?: emptyList(),
            fitKey = fitKey,
            focus = if(navigating && follow) myPt?.let { listOf(it) } else r?.points ?: myPt?.let { listOf(it) },
            satellite = satellite, threeD = threeD,
            topInsetPx = with(density) { (if(navigating) 110 else 150).dp.roundToPx() },
            bottomInsetPx = with(density) { (if (r != null) if(short) 140 else 230 else 0).dp.roundToPx() },
            onGesture = { if(navigating)follow=false },
            onLongPress = { lat, lng -> dest = Routing.Hit("Punto elegido en el mapa", lat, lng); hits = null },
        )

        Column(Modifier.statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(!navigating) Row(
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
            if(!navigating) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("A pie", foot, icon = Ic.Walk) { foot = true }
                Chip("Carro / moto", !foot, icon = Ic.Car) { foot = false }
                partner?.takeIf { it.hasLocation && !it.paused }?.let { p ->
                    Chip("Ir donde ${p.name.ifBlank { "mi pareja" }}", false, C.partner, Ic.Heart) {
                        dest = Routing.Hit(p.name.ifBlank { "Tu pareja" }, p.lat, p.lng); hits = null
                    }
                }
            }
            if(navigating) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                    Icon(Ic.Nav,null,tint=C.me,modifier=Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val toTurn = r?.steps?.take(nextIndex)?.sumOf { it.distanceM }?.minus(along)?.coerceAtLeast(0.0) ?: 0.0
                        Text(when { !fresh -> "Esperando ubicación reciente"; offRoute -> "Fuera de la ruta"; arrived -> "Llegaste a tu destino"; else -> "En ${TimeFmt.km(toTurn)}" },color=C.me)
                        Text(if(arrived)dest?.name.orEmpty() else r?.steps?.getOrNull(nextIndex)?.text.orEmpty(), color=C.text,style=androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    }
                    if(!follow) TextButton(onClick={follow=true;fitKey++}){Text("Centrar")}
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
            if (!navigating && dest == null && hits == null && !busy)
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
                    Text("${minutes(if(navigating)r.seconds*(1-along/r.distanceM.coerceAtLeast(1.0)).coerceIn(0.0,1.0) else r.seconds)} · ${TimeFmt.km((r.distanceM-along).coerceAtLeast(0.0))}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.text)
                    Text(dest?.name ?: "", color = C.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { navigating=false; arrived=false; along=0.0; dest = null; route = null }) { Icon(Ic.Close, "Quitar ruta", tint = C.sub) }
            }
            if(!navigating) Button(onClick={navigating=true;follow=true;fitKey++},enabled=fresh,modifier=Modifier.fillMaxWidth()){Text(if(fresh)"Iniciar navegación" else "Necesito una ubicación reciente")}
            if(navigating && offRoute) TextButton(onClick={dest=dest?.copy(); navigating=false; scope.launch {
                val origin=myPt ?: return@launch;val goal=dest ?: return@launch;busy=true
                route=Routing.route(origin,Pt(goal.lat,goal.lng),foot);along=0.0;offRoute=false;busy=false;fitKey++
                if(route==null)error="No se pudo recalcular. Revisa tu conexión."
            }}){Text("Recalcular ruta desde aquí")}
            if (!navigating && foot && !short) Text("Ruta por calles principales cuando es posible. Mantente en zonas iluminadas y con gente.",
                color = C.warn, fontSize = 12.sp)
            if(!navigating && !short) LazyColumn(Modifier.heightIn(max = 130.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
