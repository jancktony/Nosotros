package com.tony.nosotros.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tony.nosotros.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

/** Busca versiones nuevas en GitHub Releases, las descarga y abre el instalador. */
object Updater {
    data class Release(val version: String, val apkUrl: String, val notes: String)

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        if (BuildConfig.GH_REPO.isEmpty()) return@withContext null
        runCatching {
            val json = JSONObject(URL("https://api.github.com/repos/${BuildConfig.GH_REPO}/releases/latest").readText())
            val tag = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@runCatching null
            if (newer(tag, BuildConfig.VERSION_NAME)) Release(tag, apk.getString("browser_download_url"), json.optString("body"))
            else null
        }.getOrNull()
    }

    private fun newer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    fun downloadAndInstall(ctx: Context, r: Release) {
        val file = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "nosotros-${r.version}.apk")
        if (file.exists()) file.delete()
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(r.apkUrl))
                .setTitle("Nosotros ${r.version}")
                .setDestinationUri(Uri.fromFile(file))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        )
        val app = ctx.applicationContext
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                app.unregisterReceiver(this)
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }
}
