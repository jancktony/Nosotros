package com.tony.nosotros.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tony.nosotros.data.*
import com.tony.nosotros.util.TimeFmt
import kotlinx.coroutines.launch

@Composable
fun JourneyCard(vm: MainViewModel) {
    val pair by vm.pairId.collectAsState();val uid by vm.uid.collectAsState();val now by vm.tick.collectAsState()
    if(pair==null || !LocalFeatures.current.enabled(Feature.SHARED_TRIPS))return
    val flow=remember(pair){SharedJourneys.flow(pair!!)}
    val journeys by flow.collectAsState(initial=emptyList())
    val scope=rememberCoroutineScope();var error by remember { mutableStateOf<String?>(null) }
    journeys.filter { it.expiresAt>now }.forEach { journey ->
        Column(Modifier.fillMaxWidth().glassPanel().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(if(journey.state=="arrived") "Llegó a ${journey.name}" else "En camino a ${journey.name}",color=C.text)
            Text(if(journey.state=="arrived") "Llegada confirmada por GPS" else "Llegada estimada al iniciar: ${TimeFmt.hour(journey.etaAt)}",color=C.sub)
            if(journey.uid==uid)TextButton(onClick={scope.launch {runCatching {SharedJourneys.cancel(pair!!)}.onFailure {error="No se pudo dejar de compartir. Revisa internet."}}}){Text("Dejar de compartir")}
        }
    }
    error?.let {Text(it,color=C.danger)}
}
