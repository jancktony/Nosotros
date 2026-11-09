package com.tony.nosotros.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tony.nosotros.App
import com.tony.nosotros.data.Member
import com.tony.nosotros.data.Mode
import com.tony.nosotros.data.Place
import com.tony.nosotros.data.Repo
import com.tony.nosotros.data.Trip
import com.tony.nosotros.data.Visit
import com.tony.nosotros.tracking.TrackingService
import com.tony.nosotros.widget.Widgets
import com.tony.nosotros.util.TimeFmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = (app as App).prefs

    val pairId = MutableStateFlow(prefs.pairId)
    val onboarded = MutableStateFlow(prefs.onboarded && prefs.pairId != null)
    val uid = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)

    private fun <T> StateFlowOf(f: kotlinx.coroutines.flow.Flow<T>, init: T): StateFlow<T> =
        f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), init)

    val members: StateFlow<List<String>> = StateFlowOf(
        pairId.flatMapLatest { it?.let { Repo.pairMembersFlow(it) } ?: flowOf(emptyList()) }, emptyList()
    )
    val partnerUid: StateFlow<String?> = StateFlowOf(combine(members, uid) { m, u -> m.firstOrNull { it != u } }, null)

    val me: StateFlow<Member?> = StateFlowOf(
        combine(pairId, uid) { p, u -> p to u }.flatMapLatest { (p, u) ->
            if (p != null && u != null) Repo.memberFlow(p, u) else flowOf(null)
        }, null
    )
    val partner: StateFlow<Member?> = StateFlowOf(
        combine(pairId, partnerUid) { p, u -> p to u }.flatMapLatest { (p, u) ->
            if (p != null && u != null) Repo.memberFlow(p, u) else flowOf(null)
        }, null
    )
    val places: StateFlow<List<Place>> = StateFlowOf(
        pairId.flatMapLatest { it?.let { Repo.placesFlow(it) } ?: flowOf(emptyList()) }, emptyList()
    )

    val pairInfo: StateFlow<com.tony.nosotros.data.PairInfo> = StateFlowOf(
        pairId.flatMapLatest { it?.let { Repo.pairInfoFlow(it) } ?: flowOf(com.tony.nosotros.data.PairInfo()) }, com.tony.nosotros.data.PairInfo()
    )
    val isAdmin: StateFlow<Boolean> = StateFlowOf(combine(pairInfo, uid) { i, u -> u != null && i.admin == u }, false)
    val newCode = MutableStateFlow<String?>(null)
    fun release(r: com.tony.nosotros.update.Updater.Release) { pairId.value?.let { Repo.setRelease(it, r.version, r.apkUrl, r.notes) } }
    fun giveAdmin() { val p = pairId.value ?: return; partnerUid.value?.let { Repo.setAdmin(p, it) } }
    fun resetPartner() = viewModelScope.launch {
        val p = pairId.value ?: return@launch
        runCatching { Repo.resetPartner(p, partnerUid.value) }.onSuccess { newCode.value = it }
    }

    val webMsg = MutableStateFlow<String?>(null)
    fun linkWeb(code: String) = viewModelScope.launch {
        val p = pairId.value ?: return@launch
        runCatching { Repo.linkWeb(p, code) }
            .onSuccess { webMsg.value = "Listo, el panel web ya tiene acceso." }
            .onFailure { webMsg.value = "No se pudo: revisa el código (y que las reglas de Firebase estén actualizadas)." }
    }
    fun unlinkWeb() { pairId.value?.let { Repo.unlinkWeb(it) }; webMsg.value = "Se cerró el acceso de todos los navegadores." }

    val photos: StateFlow<List<com.tony.nosotros.data.Photo>> = StateFlowOf(
        pairId.flatMapLatest { it?.let { Repo.photosFlow(it) } ?: flowOf(emptyList()) }, emptyList()
    )
    val sending = MutableStateFlow(false)
    fun sendPhoto(thumb: ByteArray, full: ByteArray, caption: String) = viewModelScope.launch {
        val p = pairId.value ?: return@launch
        val m = me.value
        sending.value = true
        runCatching { Repo.addPhoto(p, thumb, full, caption, m?.placeName, m?.lat ?: 0.0, m?.lng ?: 0.0) }
            .onFailure { error.value = "No se pudo enviar la foto" }
        sending.value = false
    }
    fun deletePhoto(id: String) { pairId.value?.let { Repo.deletePhoto(it, id) } }
    suspend fun fullPhoto(id: String): ByteArray? = pairId.value?.let { Repo.fullPhoto(it, id) }

    val since: StateFlow<Long> = StateFlowOf(
        pairId.flatMapLatest { it?.let { Repo.sinceFlow(it) } ?: flowOf(0L) }, prefs.since
    )
    fun setSince(millis: Long) { prefs.since = millis; pairId.value?.let { Repo.setSince(it, millis) } }

    val theme = MutableStateFlow(prefs.theme)
    fun setTheme(id: String) { prefs.theme = id; theme.value = id; C.p = Palettes.of(id); Widgets.updateAll(getApplication()) }

    val satellite = MutableStateFlow(prefs.mapSatellite)
    fun toggleSatellite() { val v = !satellite.value; prefs.mapSatellite = v; satellite.value = v }

    /** Día del historial: 0 = hoy, -1 = ayer... */
    val historyDay = MutableStateFlow(0)
    val dayTrips: StateFlow<List<Trip>> = StateFlowOf(
        combine(pairId, historyDay) { p, d -> p to d }.flatMapLatest { (p, d) ->
            if (p != null) Repo.tripsFlow(p, TimeFmt.dayKeyOffset(d)) else flowOf(emptyList())
        }, emptyList()
    )
    val dayVisits: StateFlow<List<Visit>> = StateFlowOf(
        combine(pairId, historyDay) { p, d -> p to d }.flatMapLatest { (p, d) ->
            if (p != null) Repo.visitsFlow(p, TimeFmt.dayKeyOffset(d)) else flowOf(emptyList())
        }, emptyList()
    )
    val todayTrips: StateFlow<List<Trip>> = StateFlowOf(
        pairId.flatMapLatest { p -> if (p != null) Repo.tripsFlow(p, TimeFmt.dayKey()) else flowOf(emptyList()) }, emptyList()
    )
    val todayVisits: StateFlow<List<Visit>> = StateFlowOf(
        pairId.flatMapLatest { p -> if (p != null) Repo.visitsFlow(p, TimeFmt.dayKey()) else flowOf(emptyList()) }, emptyList()
    )

    /** Pulso cada 10 s para refrescar textos como "hace 30 s". */
    val tick: StateFlow<Long> = StateFlowOf(flow { while (true) { emit(System.currentTimeMillis()); delay(10_000) } }, 0L)

    init {
        if (App.firebaseConfigured) viewModelScope.launch {
            runCatching { uid.value = Repo.ensureSignedIn(); pushProfile() }
                .onFailure { error.value = "No se pudo conectar con Firebase: ${it.message}" }
        }
    }

    // ---------- Vinculación ----------
    val createdCode = MutableStateFlow<String?>(null)

    fun createPair() = viewModelScope.launch {
        runCatching { Repo.createPair() }
            .onSuccess { (id, code) -> prefs.pairId = id; pairId.value = id; createdCode.value = code; pushProfile() }
            .onFailure { error.value = "No se pudo crear el código: ${it.message}" }
    }

    fun joinCode(code: String) = viewModelScope.launch {
        runCatching { Repo.joinWithCode(code) }
            .onSuccess { id -> prefs.pairId = id; pairId.value = id; pushProfile(); Repo.cleanupCode(id) }
            .onFailure { error.value = it.message ?: "Código inválido" }
    }

    fun finishOnboarding() {
        prefs.onboarded = true
        onboarded.value = true
        pairId.value?.let { viewModelScope.launch { Repo.cleanupCode(it) } }
        TrackingService.start(getApplication())
    }

    fun unlink() = viewModelScope.launch {
        pairId.value?.let { Repo.unlink(it) }
        TrackingService.stop(getApplication())
        prefs.clearPairing(); prefs.onboarded = false; onboarded.value = false
        pairId.value = null; createdCode.value = null
    }

    // ---------- Perfil ----------
    fun setName(name: String) { prefs.myName = name.trim(); pushProfile() }
    fun setVehicle(m: Mode) { prefs.vehicle = m }

    private fun pushProfile() {
        val p = pairId.value ?: return
        Repo.updateMe(p, mapOf("name" to prefs.myName, "appVersion" to com.tony.nosotros.BuildConfig.VERSION_NAME))
    }

    // ---------- Acciones ----------
    fun requestLive() {
        val p = pairId.value ?: return
        val u = partnerUid.value ?: return
        Repo.requestLive(p, u)
    }

    fun pause(until: Long) {
        val p = pairId.value ?: return
        Repo.updateMe(p, mapOf("pausedUntil" to until))
    }

    fun resume() {
        val p = pairId.value ?: return
        Repo.updateMe(p, mapOf("pausedUntil" to 0L))
    }

    fun savePlace(place: Place) {
        val p = pairId.value ?: return
        val owner = place.owner.ifEmpty { uid.value ?: return }
        Repo.savePlace(p, place.copy(owner = owner))
    }

    fun deletePlace(id: String) { pairId.value?.let { Repo.deletePlace(it, id) } }
    fun setTripMode(tripId: String, mode: Mode) { pairId.value?.let { Repo.setTripMode(it, tripId, mode) } }
    fun refreshTracking() {
        TrackingService.deliver(getApplication(), android.content.Intent().putExtra(TrackingService.EXTRA_REFRESH, true))
    }
}
