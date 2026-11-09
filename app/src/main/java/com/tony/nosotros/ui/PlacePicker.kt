package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import com.tony.nosotros.util.Pt

/**
 * Pantalla para elegir el punto EXACTO de un lugar: se mueve el mapa
 * y el pin del centro marca la posición.
 */
@Composable
fun PlacePickerScreen(vm: MainViewModel, start: Pt?, onPick: (Double, Double) -> Unit, onCancel: () -> Unit) {
    val me by vm.me.collectAsState()
    val places by vm.places.collectAsState()
    val satellite by vm.satellite.collectAsState()
    val startPt = start ?: me?.takeIf { it.hasLocation }?.let { Pt(it.lat, it.lng) }
    var center by remember { mutableStateOf(startPt) }
    var recenter by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        LiveMap(
            Modifier.fillMaxSize(),
            people = me?.takeIf { it.hasLocation }?.let {
                listOf(MapPerson(it.uid, initialOf(it.name, "Yo"), C.me.toArgb(), C.meInk.toArgb(), it.lat, it.lng, it.mode, false))
            } ?: emptyList(),
            places = places, satellite = satellite,
            initialCenter = startPt,
            fitKey = if (recenter == 0) "none" else recenter,
            focus = me?.takeIf { it.hasLocation }?.let { listOf(Pt(it.lat, it.lng)) },
            onCenter = { lat, lng -> center = Pt(lat, lng) },
        )
        // Pin fijo en el centro
        Box(Modifier.align(Alignment.Center).offset(y = (-22).dp), contentAlignment = Alignment.Center) {
            Icon(Ic.Pin, null, tint = C.me, modifier = Modifier.size(48.dp))
        }
        Box(Modifier.align(Alignment.Center).size(8.dp).clip(CircleShape).background(C.me))

        Row(
            Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(C.glass).border(1.dp, C.line, RoundedCornerShape(20.dp)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) { Icon(Ic.Close, "Cancelar", tint = C.text) }
            Column(Modifier.weight(1f)) {
                Text("Elige el punto exacto", fontWeight = FontWeight.Bold, color = C.text, fontSize = 16.sp)
                Text("Mueve el mapa; el pin marca el lugar", color = C.sub, fontSize = 13.sp)
            }
            IconButton(onClick = { vm.toggleSatellite() }) { Icon(Ic.Layers, "Satélite", tint = C.text) }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.size(50.dp).clip(CircleShape).background(C.glass).border(1.dp, C.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { IconButton(onClick = { recenter++ }) { Icon(Ic.Locate, "Mi ubicación", tint = C.text) } }
            }
            center?.let {
                Text("%.5f, %.5f".format(it.lat, it.lng), color = C.sub, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.glass).padding(horizontal = 10.dp, vertical = 6.dp))
            }
            PrimaryButton("Usar este punto", enabled = center != null) { center?.let { onPick(it.lat, it.lng) } }
            Spacer(Modifier.width(1.dp))
        }
    }
}
