package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.nosotros.data.Member
import com.tony.nosotros.data.Place
import com.tony.nosotros.util.Eta
import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.Pt
import com.tony.nosotros.util.TimeFmt
import com.tony.nosotros.util.Together

fun initialOf(name: String, fallback: String) = (name.trim().firstOrNull() ?: fallback.first()).uppercase()

private fun Member.pairFix() = com.tony.nosotros.util.PairFix(uid,lat,lng,acc,locationAt,paused)

fun distanceBetween(a: Member?, b: Member?): Double? =
    if (a != null && b != null && a.hasLocation && b.hasLocation &&
        com.tony.nosotros.util.PairProximity.usable(listOf(a.pairFix(),b.pairFix()),System.currentTimeMillis()))
        Geo.dist(a.lat,a.lng,b.lat,b.lng) else null

private enum class Focus { BOTH, ME, PARTNER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, bottomInset: androidx.compose.ui.unit.Dp = 0.dp, onSettings: () -> Unit, onNavigate: () -> Unit, onAddPlace: (Double, Double) -> Unit, onEditPlace: (Place) -> Unit, onNavigatePlace: ((Place) -> Unit)? = null) {
    val me by vm.me.collectAsState()
    val partner by vm.partner.collectAsState()
    val places by vm.places.collectAsState()
    val partnerUid by vm.partnerUid.collectAsState()
    val trips by vm.todayTrips.collectAsState()
    val visits by vm.todayVisits.collectAsState()
    val since by vm.since.collectAsState()
    val satellite by vm.satellite.collectAsState()
    val myApp by vm.myAppPresence.collectAsState()
    val otherApp by vm.partnerAppPresence.collectAsState()
    val mySleep by vm.mySleepPresence.collectAsState()
    val otherSleep by vm.partnerSleepPresence.collectAsState()
    val presenceNow by vm.tick.collectAsState()
    val showAppActivity = LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.APP_PRESENCE)
    val activityNow = System.currentTimeMillis()
    val partnerActivity = if (showAppActivity) com.tony.nosotros.presence.AppActivity.from(otherApp,activityNow) else null
    val ownActivity = if (showAppActivity) com.tony.nosotros.presence.AppActivity.from(myApp,activityNow) else null
    var headerHeight by remember { mutableIntStateOf(0) }

    val threeD by vm.map3d.collectAsState()
    vm.tick.collectAsState().value

    var fitKey by remember { mutableIntStateOf(0) }
    var focus by remember { mutableStateOf(Focus.BOTH) }
    var liveRequested by remember { mutableStateOf(0L) }
    var selectedPerson by remember { mutableStateOf<String?>(null) }
    var selectedPlace by remember { mutableStateOf<String?>(null) }
    val sheet = rememberBottomSheetScaffoldState(bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false))
    val scope = rememberCoroutineScope()
    val sheetExpanded by remember { derivedStateOf { sheet.bottomSheetState.targetValue == SheetValue.Expanded } }
    val sheetHidden by remember { derivedStateOf { sheet.bottomSheetState.currentValue == SheetValue.Hidden || sheet.bottomSheetState.targetValue == SheetValue.Hidden } }
    // Hora estimada de llegada de la pareja mientras se mueve
    var eta by remember { mutableStateOf<Eta.Result?>(null) }
    val partnerMoving = partner?.let { !it.paused && it.mode.moving && it.speedKmh >= 3 } == true
    val lastLeft = visits.filter { it.uid == partnerUid && (it.end ?: 0L) > System.currentTimeMillis() - 3 * 3600_000L }.maxByOrNull { it.end!! }
    androidx.compose.runtime.LaunchedEffect(partnerMoving, partnerUid) {
        if (!partnerMoving) { eta = null; return@LaunchedEffect }
        while (true) {
            vm.partner.value?.let { p ->
                val dest = Eta.destination(p, vm.places.value, lastLeft?.placeName)
                eta = dest?.let { Eta.compute(Pt(p.lat, p.lng), it, p.mode) }
            }
            kotlinx.coroutines.delay(120_000)
        }
    }

    val togetherEnabled = LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.MAP_TOGETHER)
    val proximity = remember { com.tony.nosotros.util.PairProximity() }
    var together by remember { mutableStateOf(false) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(me, partner, togetherEnabled, lifecycle) {
        if (!togetherEnabled) { proximity.reset(); together = false; return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                val fixes = listOfNotNull(me, partner).map {
                    com.tony.nosotros.util.PairFix(it.uid, it.lat, it.lng, it.acc, it.locationAt, it.paused)
                }
                together = proximity.update(fixes, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime())
                kotlinx.coroutines.delay(2000)
            }
        }
    }
    val context = LocalContext.current
    // One bounded refresh on entering the map; passive background sampling is unchanged.
    androidx.compose.runtime.LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            val current = vm.me.filterNotNull().first()
            if (!current.paused && (current.locationAt <= 0 || System.currentTimeMillis()-current.locationAt > 60_000 || !current.acc.isFinite() || current.acc <= 0f || current.acc > 35f))
                com.tony.nosotros.tracking.TrackingService.deliver(context,
                    android.content.Intent().putExtra(com.tony.nosotros.tracking.TrackingService.EXTRA_REFRESH, true))
            kotlinx.coroutines.awaitCancellation()
        }
    }
    val density = LocalDensity.current
    val peekHeader = if (headerHeight > 0) with(density) { headerHeight.toDp() } + 36.dp else if (showAppActivity) 156.dp else 88.dp
    val distance = distanceBetween(me, partner)
    val confirmedTogether = together && togetherEnabled && distance != null && distance <= 70.0

    val people = buildList {
        me?.takeIf { it.hasLocation }?.let {
            add(MapPerson(it.uid, initialOf(it.name, "Yo"), C.me.toArgb(), C.meInk.toArgb(), it.lat, it.lng, it.mode, it.paused, it.speedKmh.toInt(), it.avatar))
        }
        partner?.takeIf { it.hasLocation }?.let {
            add(MapPerson(it.uid, initialOf(it.name, "P"), C.partner.toArgb(), C.partnerInk.toArgb(), it.lat, it.lng, it.mode, it.paused, it.speedKmh.toInt(), it.avatar))
        }
    }
    val routes = buildList {
        me?.let { if (it.route.isNotEmpty() && !it.paused) add(MapRoute("me", Geo.decode(it.route), C.me.toArgb())) }
        partner?.let { if (it.route.isNotEmpty() && !it.paused) add(MapRoute("p", Geo.decode(it.route), C.partner.toArgb())) }
    }
    val focusPts: List<Pt>? = when (focus) {
        Focus.BOTH -> null
        Focus.ME -> me?.takeIf { it.hasLocation }?.let { listOf(Pt(it.lat, it.lng)) }
        Focus.PARTNER -> partner?.takeIf { it.hasLocation }?.let { listOf(Pt(it.lat, it.lng)) }
    }

    BottomSheetScaffold(
        scaffoldState = sheet,
        sheetPeekHeight = peekHeader + bottomInset,
        sheetContainerColor = C.glass,
        sheetContentColor = C.text,
        sheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        sheetDragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).clip(CircleShape).background(C.sub.copy(alpha = 0.5f)))
        },
        containerColor = C.bg,
        sheetContent = {
            Box(Modifier.padding(bottom = bottomInset)) { PartnerSheet(
                partner = partner, partnerUid = partnerUid, distance = distance, since = since,
                together = confirmedTogether, collapsed = !sheetExpanded,
                timeline = buildTimeline(visits, trips, partnerUid),
                liveRequested = liveRequested, eta = eta, leftName = lastLeft?.placeName, leftAt = lastLeft?.end ?: 0L,
                appActivity = partnerActivity, sleepStatus = otherSleep.text(presenceNow,true),
                onHeaderHeight = { headerHeight = it },
                onSearch = onNavigate, quickPlaces = places.filter {it.owner==vm.uid.value && !it.suggested}.take(2),
                onQuickPlace = { onNavigatePlace?.invoke(it) }, extra = { JourneyCard(vm) },
                onLive = { vm.requestLive(); liveRequested = System.currentTimeMillis(); focus = Focus.PARTNER; fitKey++ },
            ) }
        },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            LiveMap(
                modifier = Modifier.fillMaxSize(),
                people = people, places = places, routes = routes, fitKey = fitKey, focus = focusPts,
                together = confirmedTogether,
                onTogetherTap = { selectedPerson = null; selectedPlace = null; focus = Focus.BOTH; fitKey++; scope.launch { sheet.bottomSheetState.expand() } },
                satellite = satellite, threeD = threeD,
                topInsetPx = with(density) { (if (togetherEnabled && Together.span(since, presenceNow) != null) 150.dp else 80.dp).roundToPx() },
                bottomInsetPx = with(density) { (bottomInset + if (sheetHidden) 0.dp else peekHeader).roundToPx() },
                onLongPress = onAddPlace,
                onPersonTap = { selectedPerson = it; selectedPlace = null },
                onPlaceTap = { selectedPlace = it; selectedPerson = null },
                onMapTap = { selectedPerson = null; selectedPlace = null },
                onGesture = {
                    selectedPerson = null; selectedPlace = null
                    if (sheet.bottomSheetState.targetValue != SheetValue.Hidden) scope.launch { sheet.bottomSheetState.hide() }
                },
            )
            Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TopBar(me, partner, distance, confirmedTogether, onSettings,
                    onMe = { focus = Focus.ME; fitKey++; selectedPerson = me?.uid },
                    onPartner = { focus = Focus.PARTNER; fitKey++; scope.launch { sheet.bottomSheetState.expand() } },
                    onBoth = { focus = Focus.BOTH; fitKey++ })
                if (togetherEnabled) Together.span(since, presenceNow)?.let { span ->
                    Column(Modifier.align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(20.dp)).background(C.glass)
                        .border(2.dp, Color(0xFFE8B5DE), RoundedCornerShape(20.dp))
                        .padding(horizontal = 22.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Juntos desde", color = C.text, fontSize = 12.sp)
                        Text("${span.totalDays} días", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Botones flotantes a la derecha
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomInset + if (sheetHidden) 16.dp else peekHeader + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoundButton(Ic.Nav, "Buscar dirección y ruta", onNavigate)
                RoundButton(if (satellite) Ic.Map else Ic.Layers, if (satellite) "Mapa" else "Satélite") { vm.toggleSatellite() }
                RoundButton(Ic.Locate, "Centrar") {
                    fitKey++
                    com.tony.nosotros.tracking.TrackingService.deliver(context, android.content.Intent().putExtra(com.tony.nosotros.tracking.TrackingService.EXTRA_REFRESH, true))
                }
                if (LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.MAP_3D)) TextButton(onClick = { vm.setMap3d(!threeD) }, modifier = Modifier
                    .clip(RoundedCornerShape(18.dp)).background(C.surface)) {
                    Text(if (threeD) "2D" else "3D", color = C.accent, fontWeight = FontWeight.Bold)
                }
            }
            if (sheetHidden) TextButton(onClick = { scope.launch { sheet.bottomSheetState.expand() } },
                modifier = Modifier.align(Alignment.BottomStart).padding(start=16.dp,end=16.dp,top=16.dp,bottom=16.dp+bottomInset).clip(RoundedCornerShape(24.dp)).background(C.glass)) {
                Text("Ver a ${partner?.name?.ifBlank { null } ?: "tu pareja"}  ↑", color = C.text, fontWeight = FontWeight.SemiBold)
            }
            if (people.isEmpty()) {
                Text(
                    "Esperando la primera ubicación…", color = C.text, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-80).dp)
                        .clip(RoundedCornerShape(12.dp)).background(C.glass).padding(12.dp)
                )
            }
            // Tarjeta al tocar a una persona o un lugar
            val sel: Member? = listOfNotNull(me, partner).firstOrNull { it.uid == selectedPerson }
            val selPlace = places.firstOrNull { it.id == selectedPlace }
            if (sel != null || selPlace != null) {
                Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 150.dp, start = 16.dp, end = 16.dp)) {
                    if (sel != null) PersonCard(sel, isMe = sel.uid == me?.uid, other = if (sel.uid == me?.uid) partner else me, together = confirmedTogether,
                        appActivity = if(sel.uid==me?.uid)ownActivity else partnerActivity,
                        sleepStatus = if(sel.uid==me?.uid)mySleep.text(presenceNow,true) else otherSleep.text(presenceNow,true)) { selectedPerson = null }
                    else if (selPlace != null) PlaceCard(selPlace, me, partner, vm.uid.value,
                        onEdit = { onEditPlace(selPlace); selectedPlace = null }) { selectedPlace = null }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(50.dp).clip(CircleShape).background(C.glass).border(1.dp, C.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = C.text, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun MapChip(text: String, selected: Boolean, color: Color = C.text, onClick: () -> Unit) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        color = if (selected) (if (color == C.text) C.bg else C.meInk.takeIf { color == C.me } ?: C.partnerInk) else C.text,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) color else C.glass)
            .border(1.dp, if (selected) color else C.line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun TopBar(me: Member?, partner: Member?, distance: Double?, together: Boolean, onSettings: () -> Unit,
                   onMe: () -> Unit, onPartner: () -> Unit, onBoth: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun Profile(member: Member?, mine: Boolean, modifier: Modifier, onClick: () -> Unit) {
            val accent = if (mine) C.me else C.partner
            Row(modifier.clip(RoundedCornerShape(24.dp)).background(C.glass).clickable(onClick = onClick)
                .padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(initialOf(member?.name.orEmpty(), if (mine) "Yo" else "P"), accent,
                    if (mine) C.meInk else C.partnerInk, 30.dp, photo = member?.avatar.orEmpty())
                Text(if (mine) "Yo" else member?.name?.ifBlank { null } ?: "Pareja", color = C.text, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 7.dp))
            }
        }
        Profile(me, true, Modifier.weight(1f), onMe); Profile(partner, false, Modifier.weight(1f), onPartner)
        TextButton(onClick = onBoth, modifier = Modifier.widthIn(max = 96.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
            Text(com.tony.nosotros.util.PairProximity.distanceText(together, distance), color = C.text, fontSize = 11.sp,
                maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        RoundButton(Ic.Gear, "Ajustes", onSettings)
    }
}

@Composable
private fun PersonCard(m: Member, isMe: Boolean, other: Member?, together: Boolean, appActivity: com.tony.nosotros.presence.AppActivity?, sleepStatus: String?, onClose: () -> Unit) {
    val color = if (isMe) C.me else C.partner
    FloatingCard(onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(initialOf(m.name, if (isMe) "Yo" else "P"), color, if (isMe) C.meInk else C.partnerInk, 44.dp, dim = m.paused, photo = m.avatar)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isMe) "${m.name.ifBlank { "Yo" }} (tú)" else m.name.ifBlank { "Tu pareja" }, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(statusLine(m), color = C.sub, fontSize = 14.sp)
                val d = distanceBetween(other, m) ?: distanceBetween(m, other)
                if (together || d != null) Text(com.tony.nosotros.util.PairProximity.distanceText(together, d), color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (m.acc > 0) Text("Precisión ±${m.acc.toInt()} m", color = C.sub, fontSize = 13.sp)
            }
        }
        appActivity?.let { AppActivityRow(if(isMe)"Yo" else m.name.ifBlank { "tu pareja" },it,color) }
        sleepStatus?.let { Text(it,color=C.sub,fontSize=13.sp) }
        }
    }
}

@Composable
private fun PlaceCard(p: Place, me: Member?, partner: Member?, uid: String?, onEdit: () -> Unit, onClose: () -> Unit) {
    val mine = p.owner == uid
    FloatingCard(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ic.Pin, tint = if (mine) C.me else C.partner)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.displayName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${if (mine) "Tu lugar" else "Lugar de ${partner?.name?.ifBlank { null } ?: "tu pareja"}"} · ${p.visits} visitas",
                    color = C.sub, fontSize = 14.sp)
                me?.takeIf { it.hasLocation }?.let {
                    Text("A ${TimeFmt.km(Geo.dist(it.lat, it.lng, p.lat, p.lng))} de ti", color = C.sub, fontSize = 13.sp)
                }
            }
            if (mine) Text("Editar", color = C.accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onEdit).padding(10.dp))
        }
    }
}

@Composable
private fun FloatingCard(onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface)
            .border(1.dp, C.line, RoundedCornerShape(20.dp)).clickable(onClick = onClose).padding(16.dp)
    ) { content() }
}

private fun statusLine(m: Member): String {
    if (!m.hasLocation) return "Sin ubicación todavía"
    if (m.paused) return if (m.pausedUntil > 4_000_000_000_000L) "Ubicación en pausa" else "En pausa hasta las ${TimeFmt.hour(m.pausedUntil)}"
    if (m.locationAt <= 0) return "Ubicación por confirmar"
    if (System.currentTimeMillis()-m.updatedAt > 600_000) return "Sin señal reciente · último GPS ${TimeFmt.ago(m.locationAt)}"
    if (System.currentTimeMillis()-m.locationAt > 360_000) return "Ubicación antigua · GPS ${TimeFmt.ago(m.locationAt)}"
    if (!m.acc.isFinite() || m.acc<=0) return "Ubicación sin precisión suficiente"
    val margin = kotlin.math.ceil(m.acc.toDouble()).toInt()
    if (m.acc>80) return "Ubicación aproximada · GPS ±$margin m"
    val freshness = "GPS ${TimeFmt.ago(m.locationAt)} · precisión ±$margin m"
    val activity = when {
        m.mode.moving && m.speedKmh >= 3 -> "${m.mode.label} · ${m.speedKmh.toInt()} km/h"
        m.placeName != null -> "En ${m.placeName}"
        else -> m.mode.label
    }
    return "$activity · $freshness"
}

@Composable
internal fun PartnerSheet(
    partner: Member?, partnerUid: String?, distance: Double?, since: Long, timeline: List<TimelineItem>,
    together: Boolean, collapsed: Boolean,
    liveRequested: Long, eta: Eta.Result?, leftName: String?, leftAt: Long, onLive: () -> Unit,
    appActivity: com.tony.nosotros.presence.AppActivity? = null, sleepStatus: String? = null,
    onHeaderHeight: (Int) -> Unit = {},
    onSearch: (() -> Unit)? = null, quickPlaces: List<Place> = emptyList(), onQuickPlace: (Place) -> Unit = {},
    extra: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().height(600.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (partnerUid == null) {
            Text("Tu pareja aún no se ha unido", style = MaterialTheme.typography.titleLarge, color = C.text)
            Text("Cuando escriba tu código en su teléfono aparecerá aquí.", color = C.sub)
            TogetherCard(since)
            return@Column
        }
        val name = partner?.name?.ifBlank { null } ?: "Tu pareja"
        val paused = partner?.paused == true
        Column(Modifier.fillMaxWidth().onSizeChanged { onHeaderHeight(it.height) }, verticalArrangement=Arrangement.spacedBy(10.dp)) {
        onSearch?.let { search ->
            TextButton(onClick=search,modifier=Modifier.fillMaxWidth().glassPanel(24.dp)) {
                Icon(Ic.Nav,null,tint=C.me);Spacer(Modifier.width(10.dp));Text("Buscar un lugar o dirección",color=C.text)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(initialOf(name, "P"), C.partner, C.partnerInk, 52.dp, dim = paused, photo = partner?.avatar.orEmpty())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = C.text)
                Text(partner?.let { statusLine(it) } ?: "Sin ubicación todavía", color = C.sub, fontSize = 14.sp)
            }
            if (partner != null && partner.battery >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IosBattery(partner.battery, partner.charging)
                }
            }
        }

            if(quickPlaces.isNotEmpty())Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)){
                quickPlaces.forEach {place -> TextButton(onClick={onQuickPlace(place)},modifier=Modifier.weight(1f).glassPanel(18.dp)){
                    Icon(Ic.Pin,null,tint=C.me,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(place.displayName,color=C.text,maxLines=1)
                }}}
            appActivity?.let { AppActivityRow(name,it) }
            if(LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.BATTERY_ALERTS) && partner?.shareBatteryAlerts == true && partner.battery in 0..19 && !partner.charging && System.currentTimeMillis()-partner.updatedAt in 0..600_000)
                InfoBanner(Ic.Battery,"$name tiene ${partner.battery}% de batería",C.warn)
        }

        // The collapsed sheet exposes only its header, never statistics behind the floating dock.
        if (collapsed) return@Column
        extra()

        if (eta != null && !paused) InfoBanner(Ic.Clock,
            (if (leftName != null) "Salió de $leftName a las ${TimeFmt.hour(leftAt)}. " else "") +
                "Llega a ${eta.place.displayName} aprox. a las ${TimeFmt.hour(eta.arriveAt)} (en ${Eta.mins(eta.seconds)})", C.partner)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(if (together || distance == null || distance <= 40.0) "Estado" else "Distancia", com.tony.nosotros.util.PairProximity.distanceText(together, distance), C.me, Modifier.weight(1f))
            MiniStat("Velocidad", partner?.takeIf { it.mode.moving && !paused }?.let { "${it.speedKmh.toInt()} km/h" } ?: "—", C.partner, Modifier.weight(1f))
        }

        if (partner?.saving == true && !paused) InfoBanner(Ic.Battery, "Batería baja: su teléfono está en ahorro extremo", C.warn)

        val liveActive = (partner?.liveRequestedUntil ?: 0) > System.currentTimeMillis() ||
            System.currentTimeMillis() - liveRequested < 5 * 60_000
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                .background(if (liveActive) C.partner.copy(alpha = 0.18f) else C.surface2)
                .border(1.dp, if (liveActive) C.partner else C.line, RoundedCornerShape(16.dp))
                .clickable(enabled = !paused, onClick = onLive),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Ic.Live, null, tint = if (liveActive) C.partner else C.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    paused -> "No disponible en pausa"
                    liveActive -> "En vivo · actualizando cada 3 s"
                    else -> "Ver en vivo (5 min)"
                },
                fontWeight = FontWeight.Bold, color = if (liveActive) C.partner else C.text,
            )
        }

        sleepStatus?.let { Text(it,color=C.sub,fontSize=13.sp) }
        TogetherCard(since)

        SectionLabel("Hoy")
        if (timeline.isEmpty()) Text("Todavía no hay movimientos hoy.", color = C.sub, fontSize = 14.sp)
        timeline.take(12).forEach { TimelineRow(it, C.partner) }
    }
}

@Composable
private fun MiniStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier.glassPanel(20.dp).padding(14.dp),
    ) {
        Text(label, fontSize = 12.sp, color = C.sub)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = if (value.length > 10) 15.sp else 18.sp, fontWeight = FontWeight.SemiBold, color = accent,
            maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Tarjeta con el tiempo que llevan juntos. */
@Composable
fun TogetherCard(since: Long) {
    val s = Together.span(since)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.partner.copy(alpha = 0.12f))
            .border(1.dp, C.partner.copy(alpha = 0.18f), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Heart, null, tint = C.partner, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Juntos", fontWeight = FontWeight.Bold, color = C.text)
        }
        if (s == null) {
            Text("Pon su fecha de aniversario en Ajustes para ver el contador.", color = C.sub, fontSize = 14.sp)
        } else {
            Text("${s.totalDays} días", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.partner)
            Text("${Together.longText(s)}, ${s.hours} h y ${s.minutes} min", color = C.text, fontSize = 14.sp)
            val next = Together.daysToAnniversary(since)
            if (next != null) Text(if (next == 0) "¡Hoy es su aniversario!" else "Faltan $next días para su aniversario",
                color = C.sub, fontSize = 14.sp)
        }
    }
}

@Composable
fun InfoBanner(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = C.text, fontSize = 14.sp)
    }
}
