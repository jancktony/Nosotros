package com.tony.nosotros.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tony.nosotros.App
import com.tony.nosotros.BuildConfig
import com.tony.nosotros.R
import com.tony.nosotros.data.Repo
import com.tony.nosotros.tracking.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Versiones en GitHub Releases. El administrador recibe siempre la última;
 * la pareja solo la que el administrador haya liberado. Se instala encima, sin perder datos.
 */
object Updater {
    data class Release(val version: String, val apkUrl: String, val notes: String, val date: String = "")

    private fun parse(json: JSONObject): Release? {
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
        return Release(json.getString("tag_name").removePrefix("v"), apk.getString("browser_download_url"),
            json.optString("body"), json.optString("published_at").take(10))
    }

    /** Todas las versiones publicadas, la más nueva primero. */
    suspend fun releases(): List<Release> = withContext(Dispatchers.IO) {
        if (BuildConfig.GH_REPO.isEmpty()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(URL("https://api.github.com/repos/${BuildConfig.GH_REPO}/releases?per_page=20").readText())
            (0 until arr.length()).mapNotNull { parse(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** La versión que le toca instalar a este teléfono, o null si ya está al día. */
    suspend fun target(pairId: String?): Release? {
        val info = pairId?.let { runCatching { Repo.pairInfo(it) }.getOrNull() }
        val r = if (info == null || info.admin == Repo.uid || info.relVersion == null) releases().firstOrNull()
        else Release(info.relVersion, info.relUrl, info.relNotes)
        return r?.takeIf { newer(it.version, BuildConfig.VERSION_NAME) }
    }

    fun newer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }

    /** Descarga e instala encima de la app actual. En Android 12+ no pide confirmación tras la primera vez. */
    suspend fun install(ctx: Context, r: Release): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(ctx.cacheDir, "update.apk")
            (URL(r.apkUrl).openConnection() as HttpURLConnection).run {
                instanceFollowRedirects = true; connectTimeout = 20000; readTimeout = 60000
                inputStream.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
            }
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("apk", 0, file.length()).use { o -> file.inputStream().use { it.copyTo(o) }; s.fsync(o) }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                s.commit(PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags).intentSender)
            }
            file.delete()
            true
        }.getOrDefault(false)
    }
}

/** Resultado de la instalación: si Android pide confirmar, se abre (o se avisa); al terminar, se reanuda el servicio. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val p = (ctx.applicationContext as App).prefs
            if (App.firebaseConfigured && p.onboarded && p.pairId != null) runCatching { TrackingService.start(ctx) }
            return
        }
        if (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        @Suppress("DEPRECATION")
        val confirm = (i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(confirm) }
        val n = NotificationCompat.Builder(ctx, App.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Actualización de Nosotros lista")
            .setContentText("Toca para instalarla. No se pierde nada.")
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, 77, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(77, n) }
    }
}
