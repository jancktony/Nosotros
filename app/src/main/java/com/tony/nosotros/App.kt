package com.tony.nosotros

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.tony.nosotros.data.Prefs

class App : Application() {
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        com.tony.nosotros.ui.C.p = com.tony.nosotros.ui.Palettes.of(prefs.theme)
        if (firebaseConfigured) {
            val opts = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FB_API_KEY)
                .setApplicationId(BuildConfig.FB_APP_ID)
                .setProjectId(BuildConfig.FB_PROJECT_ID)
                .build()
            if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this, opts)
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACKING, "Ubicación activa", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Aviso fijo mientras se comparte la ubicación" }
        )
        nm.cancel(99); nm.cancel(77)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Llegadas y salidas", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Avisos cuando tu pareja llega o sale de un lugar" }
        )
    }

    companion object {
        const val CH_TRACKING = "background_v2"
        const val CH_ALERTS = "alerts"
        lateinit var instance: App
            private set
        val firebaseConfigured get() = BuildConfig.FB_PROJECT_ID.isNotEmpty()
    }
}
