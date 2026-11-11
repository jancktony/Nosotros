package com.tony.nosotros.tracking

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.tony.nosotros.App

/** Recibe de Android los cambios de actividad (quieto, caminando, en vehículo...). */
class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val ev = ActivityTransitionResult.extractResult(intent)?.transitionEvents
            ?.lastOrNull { it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER } ?: return
        TrackingService.deliver(ctx, Intent()
            .putExtra(TrackingService.EXTRA_ACTIVITY, ev.activityType)
            .putExtra(TrackingService.EXTRA_ACTIVITY_ELAPSED, ev.elapsedRealTimeNanos / 1_000_000))
    }

    companion object {
        fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, ActivityReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}

/** Recibe entradas y salidas de las geocercas de los lugares. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError()) return
        val enter = when (ev.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_DWELL -> true
            Geofence.GEOFENCE_TRANSITION_EXIT -> false
            else -> return
        }
        val ids = ev.triggeringGeofences?.map { it.requestId }?.toTypedArray() ?: return
        TrackingService.deliver(
            ctx, Intent()
                .putExtra(TrackingService.EXTRA_GEOFENCE_IDS, ids)
                .putExtra(TrackingService.EXTRA_GEOFENCE_ENTER, enter),
        )
    }

    companion object {
        fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}

/** Reinicia el servicio al encender el teléfono o al actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = (ctx.applicationContext as App).prefs
        if (prefs.pairId != null && prefs.onboarded) TrackingService.start(ctx)
    }
}
