package com.tony.nosotros.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.ui.Alignment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding


import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tony.nosotros.App
import com.tony.nosotros.data.Place
import com.tony.nosotros.tracking.TrackingService
import com.tony.nosotros.util.Pt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBars()
        setContent {
            val theme by vm.theme.collectAsState()
            LaunchedEffect(theme) { applyBars() }
            val features by vm.features.collectAsState()
            androidx.compose.runtime.CompositionLocalProvider(LocalFeatures provides features) {
                NosotrosTheme { Root(vm) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = java.lang.ref.WeakReference(this)
        if (App.firebaseConfigured && com.tony.nosotros.data.Repo.authorizedSession)
            com.tony.nosotros.update.InstallProgress.restore(applicationContext)
        if (App.firebaseConfigured && com.tony.nosotros.data.Repo.authorizedSession && App.instance.prefs.receiveWalkie &&
            App.instance.prefs.featureEnabled(com.tony.nosotros.data.Feature.WALKIE)) com.tony.nosotros.voice.WalkieHub.enable(this)
    }
    override fun onPause() {
        if (foreground?.get() === this) foreground = null
        com.tony.nosotros.voice.WalkieHub.release()
        super.onPause()
    }
    companion object {
        private var foreground: java.lang.ref.WeakReference<MainActivity>? = null
        fun visibleForMicrophone() = foreground?.get()?.lifecycle?.currentState
            ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
    }

    private fun applyBars() {
        if (C.dark) enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(C.surface.toArgb()),
        ) else enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(C.surface.toArgb(), C.surface.toArgb()),
        )
    }

    override fun onStart() {
        super.onStart()
        val p = (application as App).prefs
        if (App.firebaseConfigured && p.onboarded && p.pairId != null) TrackingService.start(this)
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    if (!App.firebaseConfigured) { FirebaseMissing(); return }
    val hasSession by vm.hasSession.collectAsState()
    val account by vm.account.collectAsState()
    val skipped by vm.authSkipped.collectAsState()
    if (!hasSession) { AuthScreen(vm, upgrade = false); return }
    if (account == null && !skipped) { AuthScreen(vm, upgrade = true); return }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { com.tony.nosotros.update.InstallProgress.restore(ctx.applicationContext) }
    val walkieEnabled = LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.WALKIE)
    LaunchedEffect(account, walkieEnabled) {
        // Authentication can finish after onResume; resume only an already consented reception.
        if (com.tony.nosotros.data.Repo.authorizedSession && App.instance.prefs.receiveWalkie &&
            walkieEnabled && MainActivity.visibleForMicrophone())
            com.tony.nosotros.voice.WalkieHub.enable(ctx)
    }
    val installation by com.tony.nosotros.update.InstallProgress.state.collectAsState()
    val phoneRinging by com.tony.nosotros.find.PhoneFinder.ringing.collectAsState()
    if (phoneRinging) androidx.compose.material3.AlertDialog(onDismissRequest = { com.tony.nosotros.find.PhoneFinder.stop() },
        title = { Text("Encontrar teléfono") }, text = { Text("Se solicitó hacer sonar este teléfono desde una de sus cuentas.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { com.tony.nosotros.find.PhoneFinder.stop() }) { Text("Detener sonido") } })
    var update by remember { mutableStateOf<com.tony.nosotros.update.Updater.Release?>(null) }
    val versionPolicy by vm.pairInfo.collectAsState()
    val automaticUpdates = LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.AUTO_UPDATES)
    LaunchedEffect(versionPolicy.updateRequestAt) {
        if (!vm.releaseBusy.value) runCatching { com.tony.nosotros.update.Updater.tryAutomatic(ctx.applicationContext, vm.pairId.value) }
    }
    LaunchedEffect(automaticUpdates, versionPolicy.testVersion, versionPolicy.relVersion) {
        update = if (automaticUpdates && !vm.releaseBusy.value) runCatching { com.tony.nosotros.update.Updater.target(vm.pairId.value) }.getOrNull() else null
    }
    update?.takeIf { installation == null || installation?.terminal == true }?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { update = null }, containerColor = C.surface,
            title = { Text("Nueva versión ${r.version}") },
            text = { Text(r.notes.ifBlank { "Hay mejoras disponibles." }) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                update = null
                com.tony.nosotros.update.Updater.start(ctx.applicationContext, r)
            }) { Text("Actualizar", color = C.accent) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { update = null }) { Text("Luego", color = C.sub) } },
        )
    }
    val onboarded by vm.onboarded.collectAsState()
    Box(Modifier.fillMaxSize()) {
        if (!onboarded) OnboardingScreen(vm) else MainShell(vm)
        UpdateProgressHost()
    }
}

/** Contenedor con pestañas: Mapa · Historial · Lugares. */
@Composable
private fun MainShell(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var navDestination by remember { mutableStateOf<com.tony.nosotros.data.Routing.Hit?>(null) }
    var nav by remember { mutableStateOf(false) }
    var admin by remember { mutableStateOf(false) }
    /** Lugar en edición (nuevo o existente). */
    var editing by remember { mutableStateOf<Place?>(null) }
    /** Si no es null, se está eligiendo el punto exacto para este lugar. */
    var picking by remember { mutableStateOf<Place?>(null) }

    picking?.let { pl ->
        BackHandler { picking = null; editing = pl.takeIf { it.id.isNotEmpty() || it.name.isNotEmpty() } }
        PlacePickerScreen(
            vm, start = if (pl.lat != 0.0 || pl.lng != 0.0) Pt(pl.lat, pl.lng) else null,
            onPick = { lat, lng -> editing = pl.copy(lat = lat, lng = lng); picking = null },
            onCancel = { picking = null; editing = pl.takeIf { it.id.isNotEmpty() || it.name.isNotEmpty() } },
        )
        return
    }
    if (nav) {
        BackHandler { nav = false }
        NavScreen(vm, initialDestination = navDestination) { nav = false }
        return
    }
    if (admin) {
        BackHandler { admin = false }
        AdminScreen(vm) { admin = false }
        return
    }
    if (settings) {
        BackHandler { settings = false }
        SettingsScreen(vm, onAdmin = { admin = true }) { settings = false }
        return
    }
    BackHandler(enabled = tab != 0) { tab = 0 }

    Scaffold(
        containerColor = C.bg,
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                val voiceAvailable = LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.WALKIE) ||
                    LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.VOICE_NOTES)
                FloatingDock(tab, buildList {
                    add(DockItem(0, "Mapa", Ic.Map))
                    add(DockItem(5, "Encontrar", Ic.Locate))
                    if (voiceAvailable) add(DockItem(4, "Voz", Ic.Mic))
                    add(DockItem(6, "Más", Ic.More))
                }) { tab = it }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = if(tab == 0) 0.dp else pad.calculateBottomPadding())) {
            when (tab) {
                0 -> HomeScreen(vm, bottomInset = pad.calculateBottomPadding(), onSettings = { settings = true }, onNavigate = { navDestination = null; nav = true },
                    onAddPlace = { lat, lng -> editing = Place(lat = lat, lng = lng) },
                    onEditPlace = { editing = it }, onNavigatePlace = {navDestination = com.tony.nosotros.data.Routing.Hit(it.name,it.lat,it.lng);nav=true})
                1 -> HistoryScreen(vm)
                3 -> PhotosScreen(vm)
                4 -> VoiceScreen(vm)
                5 -> FindScreen(vm, onSettings = { settings = true }, onDirections = { member -> navDestination = com.tony.nosotros.data.Routing.Hit(member.name,member.lat,member.lng); nav = true })
                6 -> MoreScreen(onSettings = { settings = true }, onScreen = { tab = it })
                else -> PlacesScreen(vm, onAdd = { picking = Place() }, onEdit = { editing = it })
            }
        }
    }

    editing?.let { pl ->
        PlaceDialog(
            pl, if (pl.id.isEmpty()) "Nuevo lugar" else if (pl.suggested) "Nombrar lugar" else "Editar lugar",
            onSave = { vm.savePlace(it.copy(suggested = false)); editing = null },
            onMove = { picking = it; editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun FirebaseMissing() {
    Column(
        Modifier.fillMaxSize().experienceBackground().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Falta configurar Firebase", style = MaterialTheme.typography.headlineMedium)
        Text("Esta versión de la app se compiló sin el archivo google-services.json. " +
            "Envíaselo a quien compiló la app para generar el APK final.", color = C.sub)
    }
}
