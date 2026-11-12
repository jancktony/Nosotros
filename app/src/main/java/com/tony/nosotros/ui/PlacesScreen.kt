package com.tony.nosotros.ui

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

    Box(Modifier.fillMaxSize().background(C.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SettingsHero("Sus lugares", "Casa, trabajo y esos rincones especiales.", "SUS PUNTOS DE ENCUENTRO") }
            item { OutlinedTextField(search, {search=it}, label={Text("Buscar un lugar")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
            item {
                Text("Toca “Agregar lugar” y mueve el mapa hasta el punto exacto. También puedes mantener presionado el mapa principal.",
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
                            TextButton(onClick = { onEdit(s) }) { Text("Nombrar", color = C.me) }
                            IconButton(onClick = { vm.deletePlace(s.id) }) { Icon(Ic.Close, "Descartar", tint = C.sub) }
                        }
                    }
                }
            }
            item { SectionLabel("Mis lugares", Modifier.padding(top = 10.dp)) }
            if (mine.isEmpty()) item { Text("Aún no tienes lugares.", color = C.sub, fontSize = 14.sp) }
            items(mine.filter {it.name.contains(search,true)}, key = { it.id }) { PlaceRow(it, C.me, onEdit = { onEdit(it) }, onDelete = { vm.deletePlace(it.id) }) }
            item { SectionLabel("Lugares de $partnerName", Modifier.padding(top = 10.dp)) }
            if (theirs.isEmpty()) item { Text("Aún no tiene lugares.", color = C.sub, fontSize = 14.sp) }
            items(theirs.filter {it.name.contains(search,true)}, key = { it.id }) { PlaceRow(it, C.partner, onEdit = if (isAdmin) ({ onEdit(it) }) else null, onDelete = if (isAdmin) ({ vm.deletePlace(it.id) }) else null) }
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
                Text(p.name, fontWeight = FontWeight.SemiBold)
                Text("${p.visits} visitas · radio ${p.radius.toInt()} m", color = C.sub, fontSize = 13.sp)
            }
            if (onEdit != null) TextButton(onClick = onEdit) { Text("Editar", color = C.sub) }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Ic.Trash, "Eliminar", tint = C.sub) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlaceDialog(initial: Place, title: String, onSave: (Place) -> Unit, onMove: ((Place) -> Unit)? = null, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var radius by remember { mutableFloatStateOf(initial.radius.toFloat()) }
    val presets = listOf("Casa", "Trabajo", "Universidad", "Gimnasio", "Familia")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { presets.forEach { Chip(it, name == it) { name = it } } }
                Text("Radio: ${radius.toInt()} m", color = C.sub, fontSize = 14.sp)
                Slider(radius, { radius = it }, valueRange = 50f..400f, steps = 6)
                if (onMove != null) Chip("Ajustar punto exacto en el mapa", false, icon = Ic.Pin) {
                    onMove(initial.copy(name = name.trim(), radius = radius.toDouble()))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(initial.copy(name = name.trim(), radius = radius.toDouble())) }, enabled = name.isNotBlank()) {
                Text("Guardar", color = C.me)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = C.sub) } },
    )
}
