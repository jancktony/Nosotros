package com.tony.nosotros.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps3d.GoogleMap3D
import com.google.android.gms.maps3d.Map3DInitConfig
import com.google.android.gms.maps3d.Map3DView
import com.google.android.gms.maps3d.OnMap3DViewReadyCallback
import com.google.android.gms.maps3d.model.*
import com.tony.nosotros.data.Place
import com.tony.nosotros.util.Pt
import com.tony.nosotros.util.RealisticCamera

/** Only one Google 3D view is mounted, and only following an explicit tap. */
@Composable
fun RealisticMapDialog(people: List<MapPerson>, places: List<Place>, routes: List<MapRoute>, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClose) { Text("Volver al mapa") }
                    Text("3D realista", modifier = Modifier.padding(12.dp))
                }
                var selectedUid by remember { mutableStateOf<String?>(null) }
                var fitKey by remember { mutableIntStateOf(0) }
                var error by remember { mutableStateOf(false) }
                val targets = people.filter { selectedUid == null || it.uid == selectedUid }
                    .map { Pt(it.lat, it.lng) }
                if (error) {
                    Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("No se pudo cargar la vista realista. Puedes volver al mapa habitual.")
                        TextButton(onClick = onClose) { Text("Volver al mapa") }
                    }
                } else if (targets.isEmpty()) {
                    Box(Modifier.weight(1f).padding(24.dp)) { Text("Esperando una ubicación para abrir la vista realista…") }
                } else {
                    GoogleRealisticMap(Modifier.weight(1f).fillMaxWidth(), people, places, routes, targets, fitKey, onError = { error = true })
                }
                // Controls outside the map keep Google's attribution and error UI visible.
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { selectedUid = null; fitKey++ }) { Text("Ambos") }
                    people.forEach { person ->
                        TextButton(onClick = { selectedUid = person.uid; fitKey++ }) { Text(person.initial) }
                    }
                    TextButton(onClick = { fitKey++ }) { Text("Centrar") }
                }
                Text("Edificios y relieve según cobertura de Google. Para editar lugares, vuelve al mapa.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            }
        }
    }
}

@Composable
private fun GoogleRealisticMap(
    modifier: Modifier, people: List<MapPerson>, places: List<Place>, routes: List<MapRoute>,
    targets: List<Pt>, fitKey: Int, onError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handler = remember { Handler(Looper.getMainLooper()) }
    val errorCallback by rememberUpdatedState(onError)
    var map by remember { mutableStateOf<GoogleMap3D?>(null) }
    val markers = remember { mutableListOf<Marker>() }
    val lines = remember { mutableListOf<Polyline>() }
    val view = remember { Map3DView(context, Map3DInitConfig.create(mapMode = Map3DMode.HYBRID)) }
    DisposableEffect(view, lifecycle) {
        var alive = true
        var started = false
        var resumed = false
        fun sync() {
            val start = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val resume = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (resumed && !resume) { view.onPause(); resumed = false }
            if (started && !start) { view.onStop(); started = false }
            if (!started && start) { view.onStart(); started = true }
            if (!resumed && resume) { view.onResume(); resumed = true }
        }
        view.onCreate(null)
        view.getMap3DViewAsync(object : OnMap3DViewReadyCallback {
            override fun onMap3DViewReady(googleMap3D: GoogleMap3D) {
                handler.post { if (alive) map = googleMap3D }
            }
            override fun onError(error: Exception) { handler.post { if (alive) errorCallback() } }
        })
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer)
        sync()
        onDispose {
            alive = false
            lifecycle.removeObserver(observer)
            // SDK retains shared map state: remove personal overlays before destroying the view.
            markers.forEach { it.remove() }; markers.clear()
            lines.forEach { it.remove() }; lines.clear()
            if (resumed) view.onPause()
            if (started) view.onStop()
            view.onDestroy()
            map = null
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
    LaunchedEffect(map, people, places, routes) {
        val googleMap = map ?: return@LaunchedEffect
        try {
            markers.forEach { it.remove() }; markers.clear()
            lines.forEach { it.remove() }; lines.clear()
            people.forEach { person ->
                googleMap.addMarker(markerOptions {
                    position = LatLngAltitude(person.lat, person.lng, 0.0)
                    label = person.initial + if (person.paused) " · Pausado" else ""
                    altitudeMode = AltitudeMode.CLAMP_TO_GROUND
                    isDrawnWhenOccluded = true
                    collisionBehavior = CollisionBehavior.REQUIRED
                })?.let { markers.add(it) }
            }
            places.forEach { place ->
                googleMap.addMarker(markerOptions {
                    position = LatLngAltitude(place.lat, place.lng, 0.0)
                    label = place.name
                    altitudeMode = AltitudeMode.CLAMP_TO_GROUND
                })?.let { markers.add(it) }
            }
            routes.filter { it.points.size >= 2 }.forEach { route ->
                lines.add(googleMap.addPolyline(polylineOptions {
                    path = route.points.map { LatLngAltitude(it.lat, it.lng, 0.0) }
                    strokeColor = route.color
                    strokeWidth = 5.0
                    altitudeMode = AltitudeMode.CLAMP_TO_GROUND
                    drawsOccludedSegments = true
                }))
            }
        } catch (_: Exception) { errorCallback() }
    }
    // Recenter only on a deliberate control tap or first load, leaving gestures free during live updates.
    val currentTargets by rememberUpdatedState(targets)
    LaunchedEffect(map, fitKey) {
        val googleMap = map ?: return@LaunchedEffect
        val frame = RealisticCamera.frame(currentTargets) ?: return@LaunchedEffect
        try {
            googleMap.setMapMode(Map3DMode.HYBRID)
            googleMap.setCamera(camera {
                center = LatLngAltitude(frame.center.lat, frame.center.lng, 0.0)
                altitudeMode = AltitudeMode.RELATIVE_TO_GROUND
                heading = 0.0
                tilt = 65.0
                range = frame.range
            })
        } catch (_: Exception) { errorCallback() }
    }
}
