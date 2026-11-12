package com.tony.nosotros.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.*

/** Local navigation only: high accuracy while visible; never writes coordinates to the backend. */
@Composable
internal fun navigationLocation(enabled: Boolean): Location? {
    val context=LocalContext.current.applicationContext
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var fix by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(enabled,lifecycle) {
        val client=LocationServices.getFusedLocationProviderClient(context)
        var listening=false
        val callback=object:LocationCallback(){override fun onLocationResult(result:LocationResult){if(enabled)fix=result.lastLocation}}
        fun stop(){client.removeLocationUpdates(callback);listening=false;fix=null}
        fun start(){
            if(!enabled||listening||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return
            try {
                client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,2000).setMinUpdateIntervalMillis(1000).setMaxUpdateDelayMillis(2000).build(),callback,Looper.getMainLooper())
                listening=true
            }catch(_:SecurityException){stop()}
        }
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)start() else if(event==Lifecycle.Event.ON_PAUSE)stop()}
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))start()
        onDispose{lifecycle.removeObserver(observer);stop()}
    }
    return fix
}
