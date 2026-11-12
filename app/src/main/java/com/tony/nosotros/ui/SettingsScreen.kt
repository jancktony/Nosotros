package com.tony.nosotros.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.nosotros.data.Mode
import com.tony.nosotros.util.TimeFmt
import com.tony.nosotros.util.Together
import com.tony.nosotros.widget.CompactDistanceWidget
import com.tony.nosotros.widget.DaysWidget
import com.tony.nosotros.widget.DistanceWidget
import com.tony.nosotros.widget.TogetherWidget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val INDEFINITE = 4_102_444_800_000L  // año 2100 = "hasta que la reanude"

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onAdmin: () -> Unit, onBack: () -> Unit) {
    val isAdmin by vm.isAdmin.collectAsState()
    val creatorInfo by vm.pairInfo.collectAsState()
    var autoUpdate by remember { mutableStateOf(vm.prefs.autoUpdate) }
    val ctx = LocalContext.current
    val me by vm.me.collectAsState()
    val partner by vm.partner.collectAsState()
    val since by vm.since.collectAsState()
    val theme by vm.theme.collectAsState()
    val p = vm.prefs
    var name by remember { mutableStateOf(p.myName) }
    var vehicle by remember { mutableStateOf(p.vehicle) }
    var nArrive by remember { mutableStateOf(p.notifyArrive) }
    var nLeave by remember { mutableStateOf(p.notifyLeave) }
    var nPause by remember { mutableStateOf(p.notifyPause) }
    var extreme by remember { mutableStateOf(p.extremeSaving) }
    var confirmUnlink by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var allowFind by remember { mutableStateOf(p.allowFindPhone) }
    var confirmFind by remember { mutableStateOf(false) }
    var lockDetails by remember { mutableStateOf(p.lockDetails) }
    val threeD by vm.map3d.collectAsState()
    val account by vm.account.collectAsState()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Long?>(null) }
    val paused = me?.paused == true

    Column(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.activity.compose.BackHandler(enabled = category != null) { category = null }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (category == null) onBack() else category = null }) { Icon(Ic.Left, "Volver", tint = C.text) }
            Text(category ?: "Tu espacio", style = MaterialTheme.typography.titleLarge, color = C.text, fontWeight = FontWeight.Bold)
        }

        if (category == null) {
            SettingsHero(p.myName.ifBlank { "Tu espacio" }, "A tu manera, junto a ${partner?.name?.ifBlank { null } ?: "tu pareja"}") {
                Avatar(initialOf(p.myName, "Yo"), C.me, C.meInk, 54.dp, photo = me?.avatar ?: p.myAvatar)
            }
            Text("PERSONALIZA TU EXPERIENCIA", color = C.sub, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 12.dp))
            listOf(
                Triple("General", Ic.Palette, "Apariencia, mapa y actualizaciones"),
                Triple("Perfil", Ic.User, "Tu foto, nombre y vehículo"),
                Triple("Actividad", Ic.Sparkle, "Lo que decides compartir"),
                Triple("Ubicación", Ic.Pin, "Privacidad, precisión y encontrar teléfono"),
                Triple("Avisos", Ic.Bell, "Llegadas y salidas de sus lugares"),
                Triple("Widgets", Ic.Widget, "Sus días juntos y la distancia"),
                Triple("Cuenta", Ic.Link, "Vinculación y acceso seguro"),
            ).forEach { (title, icon, description) -> SettingsLink(icon, title, description) { category = title } }
            SettingsLink(Ic.Layers, "Panel web", "Encuentra tu teléfono desde cualquier dispositivo") { WebPanel.open(ctx) }
            if (isAdmin) SettingsLink(Ic.Gear, "Administración", "Tus pruebas, versiones y funciones", C.partner, onAdmin)
            Text("Creado por ${creatorInfo.creatorBrand} · ${com.tony.nosotros.BuildConfig.VERSION_NAME}", color = C.sub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
        } else SettingsHero(category.orEmpty(), when (category) {
            "General" -> "Tu apariencia y tus preferencias"
            "Perfil" -> "Así te verá tu pareja"
            "Actividad" -> "Tu información, tus decisiones"
            "Ubicación" -> "Conectados, con tu permiso"
            "Widgets" -> "Un pedacito de ustedes en tu pantalla"
            "Avisos" -> "Solo lo que te importa"
            else -> "Tu acceso privado a Nosotros"
        }, "TU ESPACIO")

        if (category == "Actividad") PresenceSettings(vm)

        // ---------- Apariencia ----------
        if (category == "General") {
            Section(Ic.Palette, "Apariencia") {
                Text("Tema", color = C.sub, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeTile("Noche", Palettes.Night, theme == "night", Modifier.weight(1f)) { vm.setTheme("night") }
                    ThemeTile("Claro", Palettes.Light, theme == "light", Modifier.weight(1f)) { vm.setTheme("light") }
                    ThemeTile("Rosa", Palettes.Pink, theme == "pink", Modifier.weight(1f)) { vm.setTheme("pink") }
                }
            }
        }

        if (category == "General" && LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.MAP_3D)) Section(Ic.Map, "Mapa") {
            Toggle("Vista 3D", threeD) { vm.setMap3d(it) }
            Text("Inclina el mapa y muestra edificios donde haya datos. En satélite se inclina la imagen; no se generan edificios fotográficos.", color = C.sub, fontSize = 13.sp)
        }

        // ---------- Nosotros ----------
        if (category == "General") {
            Section(Ic.Calendar, "Nuestro aniversario") {
                val fmt = SimpleDateFormat("d 'de' MMMM 'de' yyyy · H:mm", Locale("es"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (since > 0) fmt.format(Date(since)) else "Sin fecha", fontWeight = FontWeight.SemiBold, color = C.text)
                        Text("La ven los dos. Se usa en el contador, los widgets y los avisos de aniversario.",
                            color = C.sub, fontSize = 13.sp)
                    }
                }
                ActionButton(if (since > 0) "Cambiar fecha" else "Elegir fecha", C.partner) { pickDate = true }
            }
        }

        // ---------- Perfil ----------
        if (category == "Perfil") {
            Section(Ic.User, "Perfil") {
                if (LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.PROFILE_PHOTO)) ProfilePhotoControls(vm)
                Text(account?.let { "Cuenta: $it" } ?: "Sin cuenta de correo: ciérrala y ábrela para crearla.", color = C.sub, fontSize = 14.sp)
                OutlinedTextField(name, { name = it }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (name != p.myName && name.isNotBlank()) ActionButton("Guardar nombre", C.me) { vm.setName(name) }
                Text("Tu vehículo habitual", color = C.sub, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Carro", vehicle == Mode.CAR, icon = Ic.Car) { vehicle = Mode.CAR; vm.setVehicle(Mode.CAR) }
                    Chip("Moto", vehicle == Mode.MOTO, icon = Ic.Moto) { vehicle = Mode.MOTO; vm.setVehicle(Mode.MOTO) }
                }
                Text("Se usa cuando el teléfono detecta “en vehículo”. El GPS no distingue con certeza carro, moto o bus.",
                    color = C.sub, fontSize = 13.sp)
            }
        }

        // ---------- Compartir ----------
        if (category == "Ubicación") {
            Section(Ic.Pin, "Compartir ubicación") {
                if (paused) {
                    val until = me?.pausedUntil ?: 0
                    Text(if (until >= INDEFINITE) "En pausa hasta que la reanudes" else "En pausa hasta las ${TimeFmt.hour(until)}",
                        fontWeight = FontWeight.SemiBold, color = C.warn)
                    ActionButton("Reanudar ahora", C.me) { vm.resume() }
                } else {
                    Text("Compartiendo con tu pareja", fontWeight = FontWeight.SemiBold, color = C.ok)
                    Text("Pausar: tu pareja verá el estado en pausa.", color = C.sub, fontSize = 14.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val now = System.currentTimeMillis()
                        Chip("1 hora", false, icon = Ic.Pause) { vm.pause(now + 3_600_000) }
                        Chip("4 horas", false, icon = Ic.Pause) { vm.pause(now + 4 * 3_600_000) }
                        Chip("Hasta mañana", false, icon = Ic.Pause) { vm.pause(TimeFmt.tomorrowAt(7)) }
                        Chip("Indefinido", false, icon = Ic.Pause) { vm.pause(INDEFINITE) }
                    }
                }
            }
        }

        if (category == "Ubicación") Section(Ic.Locate, "Encontrar este teléfono") {
            Text("Consulta la última señal o solicita un sonido desde tu cuenta en la web. Necesita internet y Nosotros funcionando; respeta las pausas de ubicación.", color = C.sub, fontSize = 13.sp)
            if (LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.FIND_PHONE) || allowFind)
                Toggle("Permitir hacer sonar este teléfono", allowFind) { value ->
                    if (value) confirmFind = true else {
                        allowFind = false; p.allowFindPhone = false; com.tony.nosotros.find.PhoneFinder.stop()
                        vm.pairId.value?.let { com.tony.nosotros.data.Repo.updateMe(it, mapOf("findPhoneEnabled" to false)) }; vm.refreshTracking()
                    }
                }
            else Text("El administrador todavía no habilitó esta función.", color = C.sub, fontSize = 13.sp)
            ActionButton("Abrir panel web", C.me) { WebPanel.open(ctx) }
        }
        if (confirmFind) AlertDialog(onDismissRequest = { confirmFind = false }, title = { Text("¿Permitir encontrar este teléfono?") },
            text = { Text("Sus dos cuentas podrán solicitar un sonido de hasta 30 segundos desde la web. No cambia el volumen ni omite los controles de Android. Puedes apagar esta opción aquí.") },
            confirmButton = { TextButton(onClick = {
                allowFind = true; p.allowFindPhone = true; confirmFind = false
                vm.pairId.value?.let { com.tony.nosotros.data.Repo.updateMe(it, mapOf("findPhoneEnabled" to true)) }; vm.refreshTracking()
            }) { Text("Permitir en este teléfono") } }, dismissButton = { TextButton(onClick = { confirmFind = false }) { Text("Cancelar") } })

        // ---------- Avisos ----------
        if (category == "Avisos") {
            Section(Ic.Bell, "Avisos que recibo") {
                Toggle("Cuando llega a un lugar", nArrive) { nArrive = it; p.notifyArrive = it }
                Divider()
                Toggle("Cuando sale de un lugar", nLeave) { nLeave = it; p.notifyLeave = it }
                Divider()
                Divider()
                if (LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.LOCK_DETAILS)) Toggle("Mostrar lugar y acción en pantalla de bloqueo", lockDetails) { lockDetails = it; p.lockDetails = it }
                Text("Los avisos muestran llegó o salió y el lugar. Samsung también debe permitir mostrar el contenido de las notificaciones.", color = C.sub, fontSize = 13.sp)
                ActionButton("Ocultar aviso fijo", C.sub) {
                    ctx.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, com.tony.nosotros.App.CH_TRACKING))
                }
                Text("Oculta solo la categoría de segundo plano para conservar llegadas y salidas. Android puede mantener el indicador en Aplicaciones activas.", color = C.sub, fontSize = 12.sp)
                ActionButton("Abrir ajustes de notificaciones", C.sub) {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
            }
        }

        // ---------- Batería ----------
        if (category == "Ubicación") {
            Section(Ic.Battery, "Batería y precisión") {
                Toggle("Ahorro extremo con batería < 20 %", extreme) { extreme = it; p.extremeSaving = it; vm.refreshTracking() }
                Divider()
                val ignoring = isIgnoringBattery(ctx)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (ignoring) C.ok else C.warn))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (ignoring) "Funciona en segundo plano sin restricciones"
                        else "El teléfono puede cerrar la app en segundo plano",
                        color = C.text, fontSize = 14.sp,
                    )
                }
                if (!ignoring) ActionButton("Quitar restricción de batería", C.me) { requestIgnoreBattery(ctx) }
                ActionButton("Abrir ajustes de la app", C.sub) {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }
                Text("En Xiaomi activa también “Inicio automático”; en Samsung pon la app en “Sin restricciones”.",
                    color = C.sub, fontSize = 13.sp)
            }
        }

        // ---------- Widgets ----------
        if (category == "Widgets") {
            Section(Ic.Widget, "Widgets") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetPreview("Días juntos", Together.span(since)?.totalDays?.toString() ?: "—", C.partner, Modifier.weight(1f))
                    WidgetPreview("Distancia", "Tú + yo", C.me, Modifier.weight(1f))
                }
                Text("Elige el tamaño normal o compacto para mostrar la distancia y sus días juntos.",
                    color = C.sub, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Distancia", C.me, Modifier.weight(1f)) { pinWidget(ctx, DistanceWidget::class.java) }
                    ActionButton("Juntos", C.partner, Modifier.weight(1f)) { pinWidget(ctx, TogetherWidget::class.java) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.COMPACT_WIDGETS)) ActionButton("Distancia compacta", C.me, Modifier.weight(1f)) { pinWidget(ctx, CompactDistanceWidget::class.java) }
                    if (LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.COMPACT_WIDGETS)) ActionButton("Días compactos", C.partner, Modifier.weight(1f)) { pinWidget(ctx, DaysWidget::class.java) }
                }
                HelpDisclosure("Widgets en la pantalla de bloqueo", "Estos botones añaden widgets al inicio. Para el bloqueo en Samsung: mantén pulsada la pantalla bloqueada → Widgets. Si Nosotros no aparece, usa Good Lock → LockStar → widgets, si tu modelo lo permite. Los miniwidgets del editor de Samsung dependen de One UI y de las apps que Samsung admita.")
                ActionButton("Abrir Good Lock", C.sub) {
                    val launch = ctx.packageManager.getLaunchIntentForPackage("com.samsung.android.goodlock")
                    runCatching { ctx.startActivity(launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://galaxystore.samsung.com/detail/com.samsung.android.goodlock"))) }
                    .onFailure { android.widget.Toast.makeText(ctx, "Busca Good Lock y LockStar en Galaxy Store, si están disponibles para tu modelo.", android.widget.Toast.LENGTH_LONG).show() }
                }
            }
        }

        if (category == "General") {
            SettingsLink(Ic.Layers, "Abrir panel web", "Mapa, teléfonos y tus herramientas") { WebPanel.open(ctx) }
            Section(Ic.Sparkle, "Actualizaciones") {
                if (LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.MANUAL_UPDATES)) UpdateControls(vm.pairId.value)
                if (LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.AUTO_UPDATES)) Toggle("Instalar versiones nuevas sola (con Wi-Fi)", autoUpdate) { autoUpdate = it; p.autoUpdate = it }
                Text("Se conserva tu información. Android puede pedirte confirmar la instalación.", color = C.sub, fontSize = 13.sp)
            }
        }

        // ---------- Pareja ----------
        if (category == "Cuenta") {
            Section(Ic.Link, "Vinculación") {
                Text("Conectado con ${partner?.name?.ifBlank { null } ?: "tu pareja"}", color = C.text, fontSize = 14.sp)
                val newCode by vm.newCode.collectAsState()
                Text("Si tu pareja reinstaló la app o cambió de teléfono sin tener cuenta, crea un código nuevo para que vuelva a entrar.",
                    color = C.sub, fontSize = 13.sp)
                newCode?.let { Text("Código nuevo: $it  (dura 24 h)", color = C.me, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                if (isAdmin) ActionButton("Crear código para mi pareja", C.warn) { vm.resetPartner() }
                else Text("Solo el administrador puede regenerar el código de vinculación.", color = C.sub, fontSize = 13.sp)
                ActionButton("Desvincular", C.danger) { confirmUnlink = true }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            Text("Nosotros v${com.tony.nosotros.BuildConfig.VERSION_NAME} · Creado por ${creatorInfo.creatorBrand}", color = C.sub, fontSize = 13.sp)
        }
        Section(Ic.User, "Cuenta") {
            Text(account ?: "Cuenta personal", color = C.sub, fontSize = 14.sp)
            if (category == "Cuenta") {
                val authMessage by vm.authError.collectAsState()
                ActionButton("Cambiar mi contraseña", C.me) { account?.let { vm.forgot(it) } }
                authMessage?.let { Text(it, color = C.sub, fontSize = 13.sp) }
            }
            Text("Cerrar sesión detiene la ubicación en este teléfono. Tu pareja y el historial se conservan para cuando vuelvas a entrar.", color = C.sub, fontSize = 13.sp)
            ActionButton("Cerrar sesión", C.danger, Modifier.fillMaxWidth()) { confirmSignOut = true }
        }
        Spacer(Modifier.height(30.dp))
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = if (since > 0) toUtcDay(since) else null)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    state.selectedDateMillis?.let { pickTime = it }
                }) { Text("Siguiente", color = C.me) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar", color = C.sub) } },
            colors = DatePickerDefaults.colors(containerColor = C.surface),
        ) { DatePicker(state, title = { Text("¿Cuándo empezaron?", Modifier.padding(16.dp)) }) }
    }

    pickTime?.let { utcDay ->
        val prev = Calendar.getInstance().apply { timeInMillis = since }
        val tState = rememberTimePickerState(
            initialHour = if (since > 0) prev.get(Calendar.HOUR_OF_DAY) else 20,
            initialMinute = if (since > 0) prev.get(Calendar.MINUTE) else 0, is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { pickTime = null },
            containerColor = C.surface,
            title = { Text("¿A qué hora? (opcional)") },
            text = { TimePicker(tState, colors = TimePickerDefaults.colors(clockDialColor = C.surface2)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setSince(fromUtcDay(utcDay, tState.hour, tState.minute)); pickTime = null
                }) { Text("Guardar", color = C.me) }
            },
            dismissButton = {
                TextButton(onClick = { vm.setSince(fromUtcDay(utcDay, 0, 0)); pickTime = null }) { Text("Sin hora", color = C.sub) }
            },
        )
    }

    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false }, containerColor = C.surface,
        title = { Text("¿Cerrar sesión?") },
        text = { Text("Se detendrá la ubicación, se cerrará tu sesión y se limpiarán los widgets. Tu cuenta, pareja e historial seguirán guardados.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Cerrar sesión", color = C.danger) } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar", color = C.sub) } },
    )

    if (confirmUnlink) AlertDialog(
        onDismissRequest = { confirmUnlink = false },
        containerColor = C.surface,
        title = { Text("¿Desvincular?") },
        text = { Text("Dejarán de ver su ubicación. Para volver a conectarse necesitarán un código nuevo.") },
        confirmButton = { TextButton(onClick = { confirmUnlink = false; vm.unlink(); onBack() }) { Text("Desvincular", color = C.danger) } },
        dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("Cancelar", color = C.sub) } },
    )
}

/** El DatePicker trabaja en UTC; convertimos a la hora local elegida. */
private fun toUtcDay(local: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun fromUtcDay(utc: Long, hour: Int, minute: Int): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), hour, minute)
    }.timeInMillis
}

private fun pinWidget(ctx: Context, cls: Class<*>) {
    val mgr = AppWidgetManager.getInstance(ctx)
    if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(ComponentName(ctx, cls), null, null)
    else android.widget.Toast.makeText(ctx, "Mantén pulsado el inicio → Widgets → Nosotros", android.widget.Toast.LENGTH_LONG).show()
}

@Composable
fun Section(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(C.me.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = C.me, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = C.text)
        }
        content()
    }
}

@Composable
private fun ThemeTile(label: String, pal: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(pal.bg)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) C.me else C.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(pal.me))
            Box(Modifier.size(22.dp).clip(CircleShape).background(pal.partner))
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(pal.surface2))
        Box(Modifier.fillMaxWidth(0.6f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(pal.surface2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = pal.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (selected) Icon(Ic.Check, null, tint = pal.me, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ActionButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (color == C.sub) C.text else color, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
fun Divider() = HorizontalDivider(color = C.line)

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, color = C.text)
        Switch(value, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = C.me, checkedThumbColor = C.meInk,
            uncheckedTrackColor = C.surface2, uncheckedThumbColor = C.sub, uncheckedBorderColor = C.sub,
        ))
    }
}

fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

@android.annotation.SuppressLint("BatteryLife")
fun requestIgnoreBattery(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
    }
}
