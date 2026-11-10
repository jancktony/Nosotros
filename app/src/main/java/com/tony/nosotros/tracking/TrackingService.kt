package com.tony.nosotros.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tony.nosotros.App
import com.tony.nosotros.R
import com.tony.nosotros.data.EventItem
import com.tony.nosotros.data.Mode
import com.tony.nosotros.data.Place
import com.tony.nosotros.data.Prefs
import com.tony.nosotros.data.Repo
import com.tony.nosotros.ui.MainActivity
import com.tony.nosotros.util.TimeFmt
import com.tony.nosotros.util.Together
import com.tony.nosotros.util.Geo
import com.tony.nosotros.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en segundo plano que comparte la ubicación.
 *
 * Ahorro de batería:
 *  - QUIETO      -> sin GPS (solo lecturas "pasivas" que otras apps ya pidieron)
 *  - A PIE/BICI  -> una lectura cada ~60 s / 30 s
 *  - VEHÍCULO    -> GPS cada ~15 s
 *  - EN VIVO     -> GPS cada ~3 s durante 5 min, solo si la pareja lo pide
 *  - BATERÍA <20% -> todo más espaciado y sin GPS fino (ahorro extremo)
 *  - EN PAUSA    -> no se lee ni se envía nada
 */
class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Prefs
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var trip: TripRecorder
    private lateinit var engine: PlaceEngine

    private var pairId: String? = null
    private var me: String? = null
    private var partnerName = "Tu pareja"

    private var rawMode = Mode.UNKNOWN
    private var liveUntil = 0L
    private var pausedUntil = 0L
    private var lowBattery = false
    private var lastFix: Location? = null
    private var lastUpload = 0L
    private var lastUploadedMode = Mode.UNKNOWN
    private var fastSpeedHits = 0
    private var anchor: Location? = null
    private var lastWidgetUpdate = 0L
    private var policyKey = ""
    private var stillJob: Job? = null
    private var timerJob: Job? = null
    private var partnerJob: Job? = null
    private val notifiedEvents = HashSet<String>()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            r.locations.forEach { onLocation(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
        prefs = (application as App).prefs
        fused = LocationServices.getFusedLocationProviderClient(this)
        trip = TripRecorder(prefs)
        engine = PlaceEngine(prefs, ::onArrive, ::onLeave, ::onSuggest)
        startInForeground()

        pairId = prefs.pairId
        if (pairId == null || !App.firebaseConfigured) { stopSelf(); return }
        scope.launch {
            me = runCatching { Repo.ensureSignedIn() }.getOrNull()
            if (me == null) { stopSelf(); return@launch }
            listenFirebase()
            registerActivityTransitions()
            applyPolicy(force = true)
            requestOneFix()
            Widgets.updateAll(this@TrackingService)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handle(it) }
        return START_STICKY
    }

    override fun onDestroy() {
        running = null
        fused.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    // ---------------- Entradas externas ----------------

    fun handle(intent: Intent) {
        if (!App.firebaseConfigured || !Repo.authorizedSession || me == null) return
        if (intent.hasExtra(EXTRA_ACTIVITY)) onActivity(intent.getIntExtra(EXTRA_ACTIVITY, -1))
        if (intent.hasExtra(EXTRA_GEOFENCE_IDS)) {
            val enter = intent.getBooleanExtra(EXTRA_GEOFENCE_ENTER, true)
            intent.getStringArrayExtra(EXTRA_GEOFENCE_IDS)?.forEach { engine.onGeofence(it, enter) }
        }
        if (intent.getBooleanExtra(EXTRA_REFRESH, false)) {
            applyPolicy(force = true); requestOneFix()
        }
    }

    private fun onActivity(type: Int) {
        val newMode = when (type) {
            DetectedActivity.IN_VEHICLE -> Mode.VEHICLE
            DetectedActivity.ON_BICYCLE -> Mode.BIKE
            DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_FOOT -> Mode.WALK
            DetectedActivity.STILL -> Mode.STILL
            else -> return
        }
        setRawMode(newMode)
    }

    private fun setRawMode(newMode: Mode) {
        if (newMode == rawMode) return
        val now = System.currentTimeMillis()
        val wasStill = rawMode == Mode.STILL || rawMode == Mode.UNKNOWN
        rawMode = newMode
        trip.onRawMode(newMode, now)

        if (newMode == Mode.STILL) {
            prefs.stillSince = now
            anchor = lastFix
            engine.onStill()
            requestOneFix()
            stillJob?.cancel()
            stillJob = scope.launch {
                delay(3 * 60_000L)          // 3 min quieto = fin del trayecto
                if (rawMode == Mode.STILL) finishTrip(engine.current?.name)
            }
        } else {
            stillJob?.cancel()
            if (wasStill) {
                val since = prefs.stillSince
                val fix = lastFix
                if (since > 0 && fix != null) engine.onStayEnded(since, now, fix.latitude, fix.longitude)
                prefs.stillSince = 0L
            }
            if (!trip.active && !isPaused()) trip.begin(engine.current?.name, now)
        }
        applyPolicy()
        uploadStatus(force = true)
    }

    // ---------------- Política de ubicación (batería vs precisión) ----------------

    private fun isPaused() = pausedUntil > System.currentTimeMillis()

    @SuppressLint("MissingPermission")
    private fun applyPolicy(force: Boolean = false) {
        if (!hasLocationPermission()) return
        val now = System.currentTimeMillis()
        val live = liveUntil > now
        val (key, req) = when {
            isPaused() -> "paused" to null
            live -> "live" to LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000)
                .setMinUpdateIntervalMillis(2_000).build()
            rawMode == Mode.STILL -> "still" to LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 300_000)
                .setMinUpdateIntervalMillis(60_000).build()
            rawMode.isVehicle -> if (lowBattery)
                "veh-low" to LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000).build()
            else "veh" to LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000)
                .setMinUpdateIntervalMillis(10_000).setMinUpdateDistanceMeters(20f).build()
            rawMode == Mode.BIKE -> if (lowBattery)
                "bike-low" to LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 120_000).build()
            else "bike" to LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000)
                .setMinUpdateDistanceMeters(25f).build()
            else -> if (lowBattery)
                "walk-low" to LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 180_000).build()
            else "walk" to LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60_000)
                .setMinUpdateIntervalMillis(30_000).setMinUpdateDistanceMeters(25f).build()
        }
        if (key == policyKey && !force) return
        policyKey = key
        fused.removeLocationUpdates(callback)
        if (req != null) fused.requestLocationUpdates(req, callback, Looper.getMainLooper())

        // Programar el fin del modo en vivo o de la pausa
        timerJob?.cancel()
        val next = listOf(liveUntil, pausedUntil).filter { it > now }.minOrNull()
        if (next != null) timerJob = scope.launch {
            delay(next - now + 500)
            checkPauseTransition()
            applyPolicy()
            uploadStatus(force = true)
        }
        updateNotification()
    }

    @SuppressLint("MissingPermission")
    private fun requestOneFix() {
        if (!hasLocationPermission() || isPaused()) return
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { it?.let { loc -> onLocation(loc) } }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ---------------- Cada lectura de ubicación ----------------

    private fun onLocation(loc: Location) {
        if (isPaused()) return
        val prevFix = lastFix
        if (prevFix != null && loc.time <= prevFix.time) return
        lastFix = loc
        val speed = if (loc.hasSpeed()) loc.speed else 0f

        // Respaldo: si va rápido y Android aún no detectó vehículo
        if (speed > 8f && loc.accuracy < 40f) fastSpeedHits++ else fastSpeedHits = 0
        if (fastSpeedHits >= 2 && !rawMode.isVehicle) setRawMode(Mode.VEHICLE)

        val bm = getSystemService(BatteryManager::class.java)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val low = prefs.extremeSaving && level in 1..19 && !bm.isCharging
        if (low != lowBattery) { lowBattery = low; applyPolicy() }

        prefs.myLat = loc.latitude; prefs.myLng = loc.longitude
        if (System.currentTimeMillis() - lastWidgetUpdate > 60_000) {
            lastWidgetUpdate = System.currentTimeMillis(); Widgets.updateAll(this)
        }

        detectStillByGps(loc)
        trip.add(loc.latitude, loc.longitude, loc.accuracy, speed, loc.time)
        engine.onLocation(loc.latitude, loc.longitude, loc.accuracy, speed, settled = rawMode == Mode.STILL)
        uploadStatus()
    }

    /**
     * Respaldo por si el sensor de actividad de Android tarda o no funciona:
     *  - si en 5 min no se ha movido más de 60 m  -> quieto
     *  - si estando "quieto" aparece a más de 150 m -> se está moviendo
     */
    private fun detectStillByGps(loc: Location) {
        if (loc.accuracy > 80f) return
        val a = anchor
        if (a == null || Geo.dist(a.latitude, a.longitude, loc.latitude, loc.longitude) > 60) {
            if (rawMode == Mode.STILL && a != null &&
                Geo.dist(a.latitude, a.longitude, loc.latitude, loc.longitude) > 150) setRawMode(Mode.WALK)
            if (rawMode != Mode.STILL) anchor = loc
            return
        }
        if (rawMode != Mode.STILL && rawMode != Mode.UNKNOWN && loc.time - a.time > 5 * 60_000L) setRawMode(Mode.STILL)
        if (rawMode == Mode.UNKNOWN && loc.time - a.time > 5 * 60_000L) setRawMode(Mode.STILL)
    }

    private fun displayMode(): Mode = when {
        trip.active -> trip.classify().takeIf { it != Mode.UNKNOWN }
            ?: if (rawMode.isVehicle) prefs.vehicle else rawMode
        rawMode == Mode.VEHICLE -> prefs.vehicle
        else -> rawMode
    }

    private fun uploadStatus(force: Boolean = false) {
        val pid = pairId ?: return
        if (isPaused()) return
        val now = System.currentTimeMillis()
        val mode = displayMode()
        val minGap = if (liveUntil > now) 2_000L else 10_000L
        if (!force && now - lastUpload < minGap && mode == lastUploadedMode) return
        lastUpload = now; lastUploadedMode = mode
        val bm = getSystemService(BatteryManager::class.java)
        val fields = mutableMapOf<String, Any?>(
            "name" to prefs.myName,
            "mode" to mode.name,
            "battery" to bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            "charging" to bm.isCharging,
            "updatedAt" to now,
            "route" to if (trip.active) trip.tail() else "",
            "placeName" to engine.current?.name,
            "saving" to lowBattery,
        )
        lastFix?.let {
            fields["lat"] = it.latitude; fields["lng"] = it.longitude
            fields["acc"] = it.accuracy.toDouble()
            fields["speedKmh"] = if (it.hasSpeed() && mode.moving) it.speed * 3.6 else 0.0
        }
        Repo.updateMe(pid, fields)
        updateNotification()
    }

    // ---------------- Lugares ----------------

    private fun onArrive(p: Place) {
        val pid = pairId ?: return
        finishTrip(p.name)
        Repo.addEvent(pid, "arrive", p.name)
        Repo.incrementVisits(pid, p.id)
        prefs.currentVisitId = Repo.startVisit(pid, p.id, p.name)
        uploadStatus(force = true)
    }

    private fun onLeave(p: Place) {
        val pid = pairId ?: return
        Repo.addEvent(pid, "leave", p.name)
        prefs.currentVisitId?.let { Repo.endVisit(pid, it) }
        prefs.currentVisitId = null
        // Normalmente el trayecto ya empezó al moverse (con este lugar como origen)
        if (!trip.active) {
            trip.begin(p.name)
            trip.onRawMode(rawMode)
        }
        uploadStatus(force = true)
    }

    private fun onSuggest(lat: Double, lng: Double) {
        val pid = pairId ?: return
        Repo.savePlace(pid, Place(owner = me ?: return, name = "", lat = lat, lng = lng, radius = 100.0, suggested = true))
    }

    private fun finishTrip(to: String?) {
        val pid = pairId ?: return
        val data = trip.finish(to, me ?: return) ?: return
        Repo.saveTrip(pid, data)
    }

    @SuppressLint("MissingPermission")
    private fun registerGeofences(places: List<Place>) {
        if (!hasLocationPermission()) return
        val client = LocationServices.getGeofencingClient(this)
        val pi = GeofenceReceiver.pendingIntent(this)
        client.removeGeofences(pi).addOnCompleteListener {
            if (places.isEmpty()) return@addOnCompleteListener
            val fences = places.take(90).map {
                Geofence.Builder().setRequestId(it.id)
                    .setCircularRegion(it.lat, it.lng, maxOf(it.radius + 20, 100.0).toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL)
                    .setLoiteringDelay(90_000)
                    .setNotificationResponsiveness(60_000)
                    .build()
            }
            val req = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
                .addGeofences(fences).build()
            runCatching { client.addGeofences(req, pi) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerActivityTransitions() {
        val types = listOf(
            DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE,
            DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.STILL,
        )
        val transitions = types.map {
            ActivityTransition.Builder().setActivityType(it)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build()
        }
        runCatching {
            ActivityRecognition.getClient(this)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), ActivityReceiver.pendingIntent(this))
        }
    }

    // ---------------- Firebase: mi estado, lugares, avisos de la pareja ----------------

    private fun listenFirebase() {
        val pid = pairId ?: return
        val uid = me ?: return
        if (prefs.lastEventSeen == 0L) prefs.lastEventSeen = System.currentTimeMillis()

        scope.launch {
            Repo.memberFlow(pid, uid).collect { m ->
                if (m == null) return@collect
                var changed = false
                if (m.liveRequestedUntil != liveUntil) { liveUntil = m.liveRequestedUntil; changed = true }
                if (m.pausedUntil != pausedUntil) { pausedUntil = m.pausedUntil; changed = true }
                if (changed) {
                    checkPauseTransition()
                    applyPolicy()
                    if (liveUntil > System.currentTimeMillis()) requestOneFix()
                }
            }
        }
        scope.launch {
            Repo.placesFlow(pid).collect { all ->
                engine.allPlaces = all
                val mine = all.filter { it.owner == uid && !it.suggested && it.name.isNotBlank() }
                val changed = mine.map { listOf(it.id, it.radius, it.lat, it.lng) } != engine.places.map { listOf(it.id, it.radius, it.lat, it.lng) }
                engine.places = mine
                if (changed) registerGeofences(mine)
            }
        }
        scope.launch {
            Repo.pairMembersFlow(pid).collect { members ->
                if (uid !in members) { stopSelf(); return@collect }
                partnerJob?.cancel()
                val partner = members.firstOrNull { it != uid } ?: return@collect
                partnerJob = launch {
                    Repo.memberFlow(pid, partner).collect { m ->
                        if (m == null) return@collect
                        m.name.takeIf { it.isNotBlank() }?.let { partnerName = it; prefs.partnerName = it }
                        if (m.hasLocation && !m.paused) {
                            prefs.partnerLat = m.lat; prefs.partnerLng = m.lng
                        }
                        prefs.partnerPaused = m.paused
                        prefs.partnerUpdated = m.updatedAt
                        prefs.partnerMode = if (m.paused) "" else m.mode.name
                        prefs.partnerPlace = m.placeName
                        updateNotification()
                        Widgets.updateAll(this@TrackingService)
                    }
                }
            }
        }
        scope.launch {
            Repo.sinceFlow(pid).collect { prefs.since = it; Widgets.updateAll(this@TrackingService) }
        }
        scope.launch {
            while (true) {
                checkAnniversary()
                Widgets.updateAll(this@TrackingService)
                runCatching { autoUpdate() }
                delay(30 * 60_000L)
            }
        }
        scope.launch {
            Repo.eventsSinceFlow(pid, prefs.lastEventSeen).collect { events ->
                for (e in events) {
                    if (!notifiedEvents.add(e.id)) continue
                    if (e.at > prefs.lastEventSeen) prefs.lastEventSeen = e.at
                    if (e.uid != uid) notifyPartnerEvent(e)
                }
            }
        }
    }

    /** Publica "pausó" / "reanudó" cuando cambia el estado de pausa. */
    private fun checkPauseTransition() {
        val pid = pairId ?: return
        val paused = isPaused()
        if (paused && !prefs.wasPaused) {
            prefs.wasPaused = true
            finishTrip(null)
            fused.removeLocationUpdates(callback)
            Repo.addEvent(pid, "pause", until = pausedUntil)
        } else if (!paused && prefs.wasPaused) {
            prefs.wasPaused = false
            Repo.addEvent(pid, "resume")
            requestOneFix()
        }
    }

    private fun checkAnniversary() {
        val since = prefs.since
        val today = TimeFmt.dayKey()
        if (since <= 0 || prefs.annivNotifiedDay == today) return
        val cal = java.util.Calendar.getInstance()
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 8) return
        val span = Together.span(since) ?: return
        val text = when {
            Together.daysToAnniversary(since) == 0 -> "¡Feliz aniversario! Hoy cumplen ${span.years} ${if (span.years == 1) "año" else "años"} juntos"
            Together.isMonthiversary(since) -> "¡Feliz mesiversario! Ya son ${span.years * 12 + span.months} meses juntos"
            span.totalDays > 0 && span.totalDays % 100 == 0L -> "¡Hoy cumplen ${span.totalDays} días juntos!"
            else -> null
        } ?: return
        prefs.annivNotifiedDay = today
        val n = NotificationCompat.Builder(this, App.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(text).setAutoCancel(true)
            .setColor(0xFFF9A8D4.toInt()).setContentIntent(openAppIntent()).build()
        runCatching { getSystemService(NotificationManager::class.java).notify(99, n) }
    }

    /** Instala sola la versión que toca, una vez por versión y solo con Wi-Fi. */
    private suspend fun autoUpdate() {
        if (!prefs.autoUpdate || !com.tony.nosotros.update.Updater.onWifi(this)) return
        val r = com.tony.nosotros.update.Updater.target(pairId) ?: return
        if (prefs.autoUpdateTried == r.version) return
        if (com.tony.nosotros.update.Updater.install(applicationContext, r)) prefs.autoUpdateTried = r.version
    }

    private fun notifyPartnerEvent(e: EventItem) {
        val enabled = when (e.type) {
            "arrive" -> prefs.notifyArrive
            "leave" -> prefs.notifyLeave
            "pause", "resume" -> prefs.notifyPause
            else -> true
        }
        if (!enabled) return
        val notice = com.tony.nosotros.util.eventNotice(e.type, partnerName, e.placeName, e.at, e.until) ?: return
        postEvent(e, notice.title, notice.detail)
        // Al salir de un lugar: a dónde va probablemente y a qué hora llega
        if (e.type == "leave") scope.launch {
            val all = engine.allPlaces.filter { !it.suggested && it.name.isNotBlank() }
            val from = all.firstOrNull { it.name == e.placeName } ?: return@launch
            val dest = com.tony.nosotros.util.Eta.fallback(all.filter { it.id != from.id }, e.uid) ?: return@launch
            val r = com.tony.nosotros.util.Eta.compute(com.tony.nosotros.util.Pt(from.lat, from.lng), dest, Mode.VEHICLE) ?: return@launch
            postEvent(e, notice.title, notice.detail + ". Si va a ${dest.name}, llega aprox. a las " +
                "${TimeFmt.hour(e.at + (r.seconds * 1000).toLong())} (${com.tony.nosotros.util.Eta.mins(r.seconds)} en vehículo)")
        }
    }

    private fun postEvent(e: EventItem, title: String, text: String) {
        val n = NotificationCompat.Builder(this, App.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(e.at)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$text"))
            .setVisibility(if (prefs.lockDetails) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setColor(0xFFF9A8D4.toInt())
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(e.id.hashCode(), n) }
    }

    // ---------------- Aviso fijo del servicio ----------------

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(): Notification {
        val status = when {
            isPaused() -> "En pausa"
            liveUntil > System.currentTimeMillis() -> "Modo en vivo (tu pareja te está viendo)"
            lowBattery -> "Ahorro extremo · ${displayMode().label}"
            else -> "Compartiendo · ${displayMode().label}"
        }
        // Se ve también en la pantalla de bloqueo: distancia y dónde está la pareja
        val hasBoth = prefs.partnerUpdated > 0 && (prefs.myLat != 0.0 || prefs.myLng != 0.0) && (prefs.partnerLat != 0.0 || prefs.partnerLng != 0.0)
        val title = if (hasBoth) "A ${TimeFmt.km(com.tony.nosotros.util.Geo.dist(prefs.myLat, prefs.myLng, prefs.partnerLat, prefs.partnerLng))} de $partnerName" +
            (prefs.partnerPlace?.let { " · en $it" } ?: "") else "Nosotros"
        return NotificationCompat.Builder(this, App.CH_TRACKING)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentText(status)
            .setOngoing(true)
            .setSilent(true)
            .setColor(0xFF5EEAD4.toInt())
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun startInForeground() {
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification()) }
    }

    companion object {
        private const val NOTIF_ID = 7
        const val EXTRA_ACTIVITY = "activity"
        const val EXTRA_GEOFENCE_IDS = "gf_ids"
        const val EXTRA_GEOFENCE_ENTER = "gf_enter"
        const val EXTRA_REFRESH = "refresh"

        @Volatile var running: TrackingService? = null
            private set

        fun start(ctx: Context, extras: Intent? = null) {
            val i = Intent(ctx, TrackingService::class.java)
            extras?.extras?.let { i.putExtras(it) }
            runCatching { ContextCompat.startForegroundService(ctx, i) }
        }

        /** Entrega un evento al servicio (si ya corre, directo; si no, lo arranca). */
        fun deliver(ctx: Context, data: Intent) {
            val s = running
            if (s != null) s.handle(data) else start(ctx, data)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TrackingService::class.java))
        }
    }
}
