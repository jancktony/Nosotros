package com.tony.nosotros.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.tony.nosotros.App
import kotlinx.coroutines.flow.MutableStateFlow

object WalkieHub {
    data class State(val enabled: Boolean = false, val connected: Boolean = false,
        val talking: Boolean = false, val message: String = "Recepción desactivada")
    val state = MutableStateFlow(State())
    internal var foregroundOpen = false
    internal var service: WalkieService? = null
    fun enable(context: Context) {
        if (service != null) { service?.resumeReception(); return }
        App.instance.prefs.receiveWalkie = true
        runCatching { ContextCompat.startForegroundService(context.applicationContext, Intent(context, WalkieService::class.java)) }
            .onFailure { state.value = State(message = "Android no permitió iniciar la recepción. Abre la app para reintentar.") }
    }
    fun disable(context: Context) {
        App.instance.prefs.receiveWalkie = false
        service?.stopTalking()
        context.stopService(Intent(context, WalkieService::class.java))
        state.value = State()
    }
    fun prepare() { service?.prepare() }
    fun press() { service?.talk() }
    fun release() { service?.stopTalking() }
}
