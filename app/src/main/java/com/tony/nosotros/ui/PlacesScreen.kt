package com.tony.nosotros.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.tony.nosotros.data.Routing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.nosotros.data.Place

@Composable
fun PlacesScreen(vm: MainViewModel, onAdd: () -> Unit, onEdit: (Place) -> Unit) {
    val places by vm.places.collectAsState()
    val uid by vm.uid.collectAsState()
    val partner by vm.partner.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()

    val mine = places.filter { it.owner == uid && !it.suggested }.sortedByDescending { it.visits }
    val theirs = places.filter { it.owner != uid && !it.suggested }.sortedByDescending { it.visits }
    val suggestions = places.filter { it.owner == uid && it.suggested }
    var search by remember { mutableStateOf("") }
    val partnerName = partner?.name?.ifBlank { null } ?: "Tu pareja"

    Box(Modifier.fillMaxSize().experienceBackground()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SettingsHero("Sus lugares", "Direcciones y puntos que ustedes confirman.", "SUS PUNTOS DE ENCUENTRO") }
            item { OutlinedTextField(search, {search=it}, label={Text("Buscar un lugar")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
            item {
                Text("Escribe ciudad y dirección, revisa el resultado y confirma el pin en el mapa. También puedes ajustarlo manualmente.",
                    color = C.sub, fontSize = 14.sp)
            }
            if (suggestions.isNotEmpty()) {
                item { SectionLabel("Sugerencias", Modifier.padding(top = 10.dp)) }
                items(suggestions, key = { "s" + it.id }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Ic.Sparkle, tint = C.warn)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Vas seguido a este sitio", fontWeight = FontWeight.SemiBold)
                                Text("¿Le pones nombre?", color = C.sub, fontSize = 13.sp)
                            }
                            TextButton(onClick = { onEdit(s) }) { Text("Nombrar", color = C.accent) }
                            IconButton(onClick = { vm.deletePlace(s.id) }) { Icon(Ic.Close, "Descartar", tint = C.sub) }
                        }
                    }
                }
            }
            item { SectionLabel("Mis lugares", Modifier.padding(top = 10.dp)) }
            if (mine.isEmpty()) item { Text("Aún no tienes lugares.", color = C.sub, fontSize = 14.sp) }
            items(mine.filter {it.displayName.contains(search,true)}, key = { it.id }) { PlaceRow(it, C.me, onEdit = { onEdit(it) }, onDelete = { vm.deletePlace(it.id) }) }
            item { SectionLabel("Lugares de $partnerName", Modifier.padding(top = 10.dp)) }
            if (theirs.isEmpty()) item { Text("Aún no tiene lugares.", color = C.sub, fontSize = 14.sp) }
            items(theirs.filter {it.displayName.contains(search,true)}, key = { it.id }) { PlaceRow(it, C.partner, onEdit = if (isAdmin) ({ onEdit(it) }) else null, onDelete = if (isAdmin) ({ vm.deletePlace(it.id) }) else null) }
            item { Spacer(Modifier.padding(40.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = C.me, contentColor = C.meInk,
            icon = { Icon(Ic.Plus, null) },
            text = { Text("Agregar lugar", fontWeight = FontWeight.Bold) },
        )
    }

}

@Composable
private fun PlaceRow(p: Place, accent: androidx.compose.ui.graphics.Color, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ic.Pin, tint = accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.displayName, fontWeight = FontWeight.SemiBold)
                Text("${p.visits} visitas · radio ${p.radius.toInt()} m", color = C.sub, fontSize = 13.sp)
            }
            if (onEdit != null) TextButton(onClick = onEdit) { Text("Editar", color = C.sub) }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Ic.Trash, "Eliminar", tint = C.sub) }
        }
    }
}


@Composable
fun PlaceDialog(initial: Place, title: String, onSave: (Place) -> Unit, onMove: ((Place) -> Unit)? = null, onDismiss: () -> Unit) {
    var address by remember(initial) { mutableStateOf(initial.address) }
    var city by remember(initial) { mutableStateOf(initial.city) }
    var radius by remember(initial) { mutableFloatStateOf(initial.radius.toFloat()) }
    var confirmed by remember(initial) { mutableStateOf((initial.lat != 0.0 || initial.lng != 0.0) && (initial.address.isNotBlank() || initial.id.isNotBlank())) }
    var busy by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf(emptyList<Routing.Hit>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun draft(lat: Double = initial.lat, lng: Double = initial.lng) = initial.copy(
        name = initial.name.ifBlank { address.trim() }, address = address.trim(), city = city.trim(),
        radius = radius.toDouble(), lat = lat, lng = lng)
    val legacy = address.isBlank() && city.isBlank() && initial.id.isNotBlank() && initial.name.isNotBlank()
    AlertDialog(onDismissRequest = onDismiss, containerColor = C.surface, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(city, { city = it.take(80); confirmed = false; hits = emptyList(); message = null },
                    label = { Text("Ciudad") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it.take(200); confirmed = false; hits = emptyList(); message = null },
                    label = { Text("Dirección exacta") }, placeholder = { Text("Calle 56 #43-20") },
                    enabled = !busy, modifier = Modifier.fillMaxWidth())
                if (legacy) Text("Lugar anterior: ${initial.name}. Puedes conservarlo o añadir su dirección.", color = C.sub, fontSize = 12.sp)
                Text("La dirección será el título del lugar. El pin debe señalar el edificio.", color = C.sub, fontSize = 12.sp)
                TextButton(enabled = !busy && address.isNotBlank() && city.isNotBlank() && onMove != null, onClick = {
                    scope.launch {
                        busy = true; hits = emptyList(); message = null
                        try {
                            val result = Routing.search("${address.trim()}, ${city.trim()}", null)
                            hits = result.hits.take(4)
                            message = if (hits.isNotEmpty()) "Elige un resultado y revisa el pin; una coincidencia puede ser aproximada."
                                else if (result.failure == Routing.SearchFailure.UNAVAILABLE) "La búsqueda no está disponible. Puedes ubicar el pin manualmente."
                                else "No se encontró esa dirección. Revisa la ciudad o ubica el pin manualmente."
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "Buscando…" else "Buscar dirección") }
                message?.let { Text(it, color = C.sub, fontSize = 12.sp) }
                hits.forEach { hit ->
                    TextButton(onClick = { onMove?.invoke(draft(hit.lat, hit.lng)) }, modifier = Modifier.fillMaxWidth()) {
                        Column { Text(hit.name); Text(hit.detail, color = C.sub, fontSize = 11.sp); Text("Revisar este punto en el mapa", fontSize = 12.sp) }
                    }
                }
                if (onMove != null) TextButton(enabled = !busy, onClick = { onMove(draft()) }) {
                    Text(if (confirmed) "Ajustar punto en el mapa" else "Confirmar punto en el mapa")
                }
                if (confirmed) Text("Punto confirmado · ${draft().displayName}", color = C.accent, fontSize = 12.sp)
                Text("Radio: ${radius.toInt()} m", color = C.sub, fontSize = 14.sp)
                Slider(radius, { radius = it }, valueRange = 50f..400f, steps = 6)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && confirmed && (legacy || address.isNotBlank() && city.isNotBlank()), onClick = { onSave(draft()) }) { Text("Guardar", color = C.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = C.sub) } })
}
