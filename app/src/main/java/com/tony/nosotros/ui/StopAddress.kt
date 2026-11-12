package com.tony.nosotros.ui

import android.location.Geocoder
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.tony.nosotros.util.Pt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Reverse geocoding runs on demand, without uploading the address or changing a visit. */
@Composable
fun StopAddress(point: Pt?, color: Color = C.sub) {
    val context = LocalContext.current.applicationContext
    val address by produceState<String?>(null, point) {
        value = if (point == null) "Dirección no disponible para esta parada" else withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale("es", "CO")).getFromLocation(point.lat, point.lng, 1)?.firstOrNull()?.getAddressLine(0)
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "$it · dirección aproximada según GPS" }
                ?: "Dirección no disponible · ${"%.5f".format(Locale.US,point.lat)}, ${"%.5f".format(Locale.US,point.lng)}"
        }
    }
    Text(address ?: "Buscando dirección…", color = color, fontSize = 12.sp)
}
