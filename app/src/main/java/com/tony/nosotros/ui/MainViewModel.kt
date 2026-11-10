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
        pairId.flatMapLatest { it?.let { Repo.pairInfoFlow(it) } ?: flowOf(com.tony.nosotros.data.PairInfo()) }, com.tony.nosotros.data.PairInfo(featureFlags = prefs.featureModes)
    )
    val isAdmin: StateFlow<Boolean> = StateFlowOf(combine(pairInfo, uid) { i, u -> u != null && i.admin == u }, false)
    val features = combine(pairInfo, uid) { info, user ->
        com.tony.nosotros.data.FeatureAccess(info.featureFlags, user != null && info.admin == user)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, com.tony.nosotros.data.FeatureAccess(prefs.featureModes, prefs.featureAdmin))
    val featureBusy = MutableStateFlow(false)
    fun setFeature(feature: com.tony.nosotros.data.Feature, mode: String) = viewModelScope.launch {
        if (!isAdmin.value || featureBusy.value) return@launch
        val id = pairId.value ?: return@launch
        featureBusy.value = true
        try {
            Repo.setFeature(id, feature, mode)
            adminMessage.value = "${feature.title}: configuración guardada."
        } catch (_: Exception) { adminMessage.value = "No se pudo guardar. Revisa conexión y reglas de Firebase." }
        finally { featureBusy.value = false }
    }
    val newCode = MutableStateFlow<String?>(null)
    fun release(r: com.tony.nosotros.update.Updater.Release) = viewModelScope.launch {
        val id = pairId.value ?: return@launch
        if (!isAdmin.value) return@launch
        runCatching { Repo.setRelease(id, r.version, r.apkUrl, r.notes) }
            .onSuccess { adminMessage.value = "Versión ${r.version} habilitada para tu pareja." }
            .onFailure { adminMessage.value = "No se pudo liberar la versión. Revisa la conexión." }
    }
    fun giveAdmin() { val p = pairId.value ?: return; partnerUid.value?.let { Repo.setAdmin(p, it) } }
    fun resetPartner() = viewModelScope.launch {
        val p = pairId.value ?: return@launch
        if (!isAdmin.value) return@launch
        runCatching { Repo.resetPartner(p, partnerUid.value) }.onSuccess { newCode.value = it }
            .onFailure { error.value = "No se pudo regenerar el código." }
    }

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

    val map3d = MutableStateFlow(prefs.map3d)
    fun setMap3d(value: Boolean) { if (value && !features.value.enabled(com.tony.nosotros.data.Feature.MAP_3D)) return; prefs.map3d = value; map3d.value = value }
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
        viewModelScope.launch { features.collect {
            prefs.featureModes = it.modes; prefs.featureAdmin = it.admin
            Widgets.updateAll(getApplication())
        } }
        if (App.firebaseConfigured) viewModelScope.launch {
            if (Repo.authorizedSession) Repo.uid?.let { uid.value = it; runCatching { restore() } }
        }
    }

    // ---------- Cuenta ----------
    val account = MutableStateFlow(if (App.firebaseConfigured) Repo.email else null)
    val hasSession = MutableStateFlow(App.firebaseConfigured && Repo.authorizedSession)
    val authSkipped = MutableStateFlow(false)
    val authBusy = MutableStateFlow(false)
    val authError = MutableStateFlow<String?>(null)

    fun auth(email: String, pass: String, create: Boolean) = viewModelScope.launch {
        authBusy.value = true; authError.value = null
        runCatching { if (create) Repo.register(email, pass) else Repo.login(email, pass) }
            .onSuccess { uid.value = it; hasSession.value = true; account.value = Repo.email; runCatching { restore() } }
            .onFailure { authError.value = authMessage(it) }
        authBusy.value = false
    }

    fun forgot(email: String) = viewModelScope.launch {
        runCatching { Repo.resetPassword(email) }
            .onSuccess { authError.value = "Te enviamos un correo para cambiar la contraseña." }
            .onFailure { authError.value = authMessage(it) }
    }

    private fun authMessage(e: Throwable): String = when {
        e is IllegalStateException || e is IllegalArgumentException -> e.message ?: "Acceso privado."
        e is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "Ese correo ya tiene cuenta. Usa “iniciar sesión”."
        e is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "La contraseña debe tener al menos 6 caracteres."
        e is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "No hay ninguna cuenta con ese correo."
        e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
        e.message.orEmpty().contains("not allowed", true) || e.message.orEmpty().contains("disabled", true) ->
            "Falta activar el acceso con correo en Firebase."
        else -> "No se pudo. Revisa tu internet e inténtalo de nuevo."
    }

    /** Tras iniciar sesión en una instalación nueva, recupera la pareja y el nombre de esta cuenta. */
    private suspend fun restore() {
        if (prefs.pairId == null) Repo.findMyPair()?.let { id ->
            Repo.myName(id)?.takeIf { it.isNotBlank() }?.let { prefs.myName = it }
            prefs.myAvatar = Repo.myAvatar(id)
            prefs.pairId = id; pairId.value = id
            prefs.onboarded = true; onboarded.value = true
            TrackingService.start(getApplication())
        }
        pushProfile()
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
        val result = runCatching { pairId.value?.let { Repo.unlink(it) } }
        if (result.isFailure) {
            error.value = "No se pudo desvincular. Si eres administrador, cede primero la administración."
            return@launch
        }
        TrackingService.stop(getApplication())
        prefs.clearPairing(); prefs.onboarded = false; onboarded.value = false
        pairId.value = null; createdCode.value = null
    }

    fun signOut() {
        TrackingService.stop(getApplication())
        Repo.signOut()
        pairId.value = null; uid.value = null; onboarded.value = false
        prefs.clearSession()
        profileBusy.value = false; profileMessage.value = null; adminMessage.value = null
        account.value = null; hasSession.value = false; authSkipped.value = false
        createdCode.value = null; newCode.value = null; error.value = null; authError.value = null
        historyDay.value = 0
        getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancelAll()
        Widgets.updateAll(getApplication())
    }

    val profileBusy = MutableStateFlow(false)
    val profileMessage = MutableStateFlow<String?>(null)
    fun setAvatar(encoded: String) = viewModelScope.launch {
        if (!features.value.enabled(com.tony.nosotros.data.Feature.PROFILE_PHOTO)) return@launch
        val id = pairId.value ?: return@launch
        val session = uid.value
        profileBusy.value = true; profileMessage.value = null
        runCatching { Repo.saveAvatar(id, encoded) }
            .onSuccess { if (uid.value != session) return@onSuccess; prefs.myAvatar = encoded; profileMessage.value = if (encoded.isEmpty()) "Foto eliminada." else "Foto de perfil guardada." }
            .onFailure { profileMessage.value = "No se pudo guardar la foto. Revisa la conexión e inténtalo de nuevo." }
        profileBusy.value = false
    }

    val adminMessage = MutableStateFlow<String?>(null)
    fun allowAutomaticPartnerUpdates() = viewModelScope.launch {
        if (!isAdmin.value) return@launch
        val id = pairId.value ?: return@launch
        runCatching { Repo.automaticPartnerUpdates(id) }
            .onSuccess { adminMessage.value = "Tu pareja recibirá las versiones nuevas automáticamente si tiene activada la opción en su teléfono." }
            .onFailure { adminMessage.value = "No se pudo cambiar la política de versiones." }
    }

    // ---------- Perfil ----------
    fun setName(name: String) { prefs.myName = name.trim(); pushProfile() }
    fun setVehicle(m: Mode) { prefs.vehicle = m }

    private fun pushProfile() {
        val p = pairId.value ?: return
        Repo.updateMe(p, mapOf("name" to prefs.myName, "appVersion" to com.tony.nosotros.BuildConfig.VERSION_NAME))
    }

    // ---------- Acciones ----------
    fun requestLive() = viewModelScope.launch {
        val p = pairId.value ?: return@launch
        val u = partnerUid.value ?: return@launch
        runCatching { Repo.requestLive(p, u) }
            .onSuccess { adminMessage.value = "Solicitud enviada. Se respeta la pausa de ubicación de tu pareja." }
            .onFailure { error.value = "No se pudo solicitar la ubicación. Revisa tu conexión." }
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
