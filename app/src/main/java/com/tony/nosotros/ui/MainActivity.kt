package com.tony.nosotros.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import kotlinx.coroutines.launch

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
        consumeUpdateConsent()
        if (App.firebaseConfigured && com.tony.nosotros.data.Repo.authorizedSession && App.instance.prefs.receiveWalkie &&
            App.instance.prefs.featureEnabled(com.tony.nosotros.data.Feature.WALKIE)) com.tony.nosotros.voice.WalkieHub.enable(this)
    }
    override fun onPause() {
        if (foreground?.get() === this) foreground = null
        com.tony.nosotros.voice.WalkieHub.release()
        super.onPause()
    }
    private fun consumeUpdateConsent() {
        val prefs = App.instance.prefs
        if (!App.firebaseConfigured || !com.tony.nosotros.data.Repo.authorizedSession) return
        val intent = com.tony.nosotros.update.UpdateConsent.pending ?: prefs.pendingUpdateConsent?.let {
            runCatching { android.content.Intent.parseUri(it, android.content.Intent.URI_INTENT_SCHEME) }.getOrNull()
        } ?: return
        com.tony.nosotros.update.UpdateConsent.pending = null; prefs.pendingUpdateConsent = null
        runCatching { startActivity(intent) }.onFailure { prefs.autoUpdateTried = "" }
    }
    companion object {
        private var foreground: java.lang.ref.WeakReference<MainActivity>? = null
        fun visibleForMicrophone() = foreground?.get()?.lifecycle?.currentState
            ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
        fun showPendingUpdate() { foreground?.get()?.let { activity -> activity.runOnUiThread { activity.consumeUpdateConsent() } } }
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
    val phoneRinging by com.tony.nosotros.find.PhoneFinder.ringing.collectAsState()
    if (phoneRinging) androidx.compose.material3.AlertDialog(onDismissRequest = { com.tony.nosotros.find.PhoneFinder.stop() },
        title = { Text("Encontrar teléfono") }, text = { Text("Se solicitó hacer sonar este teléfono desde una de sus cuentas.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { com.tony.nosotros.find.PhoneFinder.stop() }) { Text("Detener sonido") } })
    var update by remember { mutableStateOf<com.tony.nosotros.update.Updater.Release?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val versionPolicy by vm.pairInfo.collectAsState()
    val automaticUpdates = LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.AUTO_UPDATES)
    LaunchedEffect(versionPolicy.updateRequestAt) {
        if (!vm.releaseBusy.value) runCatching { com.tony.nosotros.update.Updater.tryAutomatic(ctx.applicationContext, vm.pairId.value) }
    }
    LaunchedEffect(automaticUpdates, versionPolicy.testVersion, versionPolicy.relVersion) {
        update = if (automaticUpdates && !vm.releaseBusy.value) runCatching { com.tony.nosotros.update.Updater.target(vm.pairId.value) }.getOrNull() else null
    }
    update?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { update = null }, containerColor = C.surface,
            title = { Text("Nueva versión ${r.version}") },
            text = { Text(r.notes.ifBlank { "Hay mejoras disponibles." }) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                update = null
                Toast.makeText(ctx, "Descargando… se instalará sola", Toast.LENGTH_LONG).show()
                scope.launch {
                    if (!com.tony.nosotros.update.Updater.install(ctx.applicationContext, r))
                        Toast.makeText(ctx, "No se pudo descargar. Revisa tu internet.", Toast.LENGTH_LONG).show()
                }
            }) { Text("Actualizar", color = C.me) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { update = null }) { Text("Luego", color = C.sub) } },
        )
    }
    val onboarded by vm.onboarded.collectAsState()
    if (!onboarded) OnboardingScreen(vm) else MainShell(vm)
}

/** Contenedor con pestañas: Mapa · Historial · Lugares. */
@Composable
private fun MainShell(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
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
        NavScreen(vm) { nav = false }
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
            Box(Modifier.background(C.bg).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
            NavigationBar(containerColor = C.surface, tonalElevation = 0.dp,
                windowInsets = WindowInsets(0,0,0,0), modifier = Modifier.height(74.dp).clip(RoundedCornerShape(25.dp))) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = C.me, selectedTextColor = C.me, indicatorColor = C.me.copy(alpha = 0.16f),
                    unselectedIconColor = C.sub, unselectedTextColor = C.sub,
                )
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Ic.Map, null) }, label = { Text("Mapa", fontWeight = FontWeight.SemiBold) }, colors = colors)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Ic.Clock, null) }, label = { Text("Historial", fontWeight = FontWeight.SemiBold) }, colors = colors)
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Ic.Pin, null) }, label = { Text("Lugares", fontWeight = FontWeight.SemiBold) }, colors = colors)
                if (LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.WALKIE) || LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.VOICE_NOTES))
                    NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Ic.Mic, null) }, label = { Text("Voz") }, colors = colors)
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Ic.Layers, null) }, label = { Text("Galería", fontWeight = FontWeight.SemiBold) }, colors = colors)
            }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            when (tab) {
                0 -> HomeScreen(vm, onSettings = { settings = true }, onNavigate = { nav = true },
                    onAddPlace = { lat, lng -> editing = Place(lat = lat, lng = lng) },
                    onEditPlace = { editing = it })
                1 -> HistoryScreen(vm)
                3 -> PhotosScreen(vm)
                4 -> VoiceScreen(vm)
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
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Falta configurar Firebase", style = MaterialTheme.typography.headlineMedium)
        Text("Esta versión de la app se compiló sin el archivo google-services.json. " +
            "Envíaselo a quien compiló la app para generar el APK final.", color = C.sub)
    }
}
