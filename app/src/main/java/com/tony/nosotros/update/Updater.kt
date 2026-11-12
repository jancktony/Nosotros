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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import android.content.pm.PackageManager
import java.io.File

/**
 * Versiones privadas en Firestore, sin bucket ni dependencia de GitHub. El administrador recibe siempre la última;
 * la pareja solo la que el administrador haya liberado. Se instala encima, sin perder datos.
 */
object Updater {
    private val installationMutex = Mutex()
    @Volatile private var pendingVersion: String? = null
    fun finishInstallAttempt() { pendingVersion = null }
    data class Release(val version: String, val apkUrl: String, val notes: String, val date: String = "")

    /** Manifiestos privados publicados por CI; no hay tokens de GitHub en el APK. */
    suspend fun releases(): List<Release> = withContext(Dispatchers.IO) {
        val privateResult = runCatching {
            check(Repo.authorizedSession) { "Verifica tu correo para acceder a las actualizaciones privadas." }
            Repo.db.collection("releases").orderBy("publishedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20).get().await().documents.mapNotNull { d ->
                    if (d.getString("pairId") != App.instance.prefs.pairId) return@mapNotNull null
                    val version = d.getString("version") ?: return@mapNotNull null
                    val path = d.getString("apkPath") ?: return@mapNotNull null
                    if (d.getBoolean("ready") != true || !path.startsWith("firestore://releases/")) return@mapNotNull null
                    Release(version, path, d.getString("notes").orEmpty(), d.getString("date").orEmpty())
                }
        }
        privateResult.getOrThrow()
    }

    /** La versión que le toca instalar a este teléfono, o null si ya está al día. */
    suspend fun target(pairId: String?): Release? {
        if (!Repo.authorizedSession || pairId == null) return null
        val info = Repo.pairInfo(pairId)
        val admin = info.admin == Repo.uid
        val version = UpdatePolicy.targetVersion(admin, info.testVersion, info.relVersion) ?: return null
        if (!newer(version, BuildConfig.VERSION_NAME) || pendingVersion == version) return null
        val release = releases().firstOrNull { it.version == version }
            ?: if (!admin && info.relUrl.startsWith("firestore://releases/")) Release(version, info.relUrl, info.relNotes) else null
        return release
    }

    suspend fun tryAutomatic(ctx: Context, pairId: String?) {
        if (pairId == null || !Repo.authorizedSession) return
        val prefs = App.instance.prefs
        val info = Repo.pairInfo(pairId)
        val release = target(pairId) ?: return
        val admin = info.admin == Repo.uid
        if (!UpdatePolicy.automaticAllowed(admin, release.version, info.forceUpdateVersion, info.updateRequestAt,
                prefs.handledForceUpdate, prefs.autoUpdate, prefs.featureEnabled(com.tony.nosotros.data.Feature.AUTO_UPDATES),
                onWifi(ctx), info.forceAllowMetered)) return
        val forced = !admin && info.forceUpdateVersion == release.version && info.updateRequestAt > prefs.handledForceUpdate
        if (!forced && prefs.autoUpdateTried == release.version) return
        if (install(ctx, release)) {
            prefs.autoUpdateTried = release.version
            if (forced) prefs.handledForceUpdate = info.updateRequestAt
        }
    }
    fun recordStatus(status: String, version: String = "") {
        val p = App.instance.prefs.pairId ?: return
        if (Repo.authorizedSession) Repo.updateMe(p, mapOf("updateStatus" to status, "updateTarget" to version))
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
        installationMutex.withLock {
        if (pendingVersion != null) return@withLock pendingVersion == r.version
        runCatching {
            recordStatus("downloading", r.version)
            val file = File(ctx.cacheDir, "update.apk")
            check(Repo.authorizedSession) { "Acceso privado" }
            PrivateApk.download(r.version, r.apkUrl, file)

            @Suppress("DEPRECATION")
            val archive = ctx.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: error("APK inválido")
            require(archive.packageName == ctx.packageName) { "Paquete incorrecto" }
            @Suppress("DEPRECATION")
            val installed = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val currentSigners = installed.signingInfo?.apkContentsSigners ?: error("Firma no disponible")
            val updateSigners = archive.signingInfo?.apkContentsSigners ?: error("APK sin firma")
            require(currentSigners.toSet() == updateSigners.toSet()) { "La firma de la actualización no coincide" }
            require(archive.longVersionCode > installed.longVersionCode) { "Versión antigua" }
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("apk", 0, file.length()).use { o -> file.inputStream().use { it.copyTo(o) }; s.fsync(o) }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                pendingVersion = r.version
                recordStatus("installing", r.version)
                s.commit(PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags).intentSender)
            }
            file.delete()
            true
        }.getOrDefault(false).also { if (!it) { finishInstallAttempt(); recordStatus("failed", r.version) }; File(ctx.cacheDir, "update.apk").delete() }
        }
    }
}

/** Resultado de la instalación: si Android pide confirmar, se abre (o se avisa); al terminar, se reanuda el servicio. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val p = (ctx.applicationContext as App).prefs
            if (Repo.authorizedSession) {
                Updater.recordStatus("installed", BuildConfig.VERSION_NAME)
                p.pairId?.let { Repo.updateMe(it, mapOf("appVersion" to BuildConfig.VERSION_NAME)) }
            }
            if (App.firebaseConfigured && p.onboarded && p.pairId != null) runCatching { TrackingService.start(ctx) }
            return
        }
        val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Updater.finishInstallAttempt()
            UpdateConsent.pending = null
            (ctx.applicationContext as App).prefs.pendingUpdateConsent = null
            Updater.recordStatus(if (status == PackageInstaller.STATUS_SUCCESS) "installed" else "failed")
            if (status != PackageInstaller.STATUS_SUCCESS) (ctx.applicationContext as App).prefs.autoUpdateTried = ""
            return
        }
        @Suppress("DEPRECATION")
        val confirm = (i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Repo.authorizedSession) {
            Updater.recordStatus("confirmation")
            UpdateConsent.pending = confirm
            (ctx.applicationContext as App).prefs.pendingUpdateConsent = confirm.toUri(Intent.URI_INTENT_SCHEME)
            com.tony.nosotros.ui.MainActivity.showPendingUpdate()
        }

    }
}

/** Android confirmation remains inside the app; no extra status-bar notification. */
object UpdateConsent { var pending: Intent? = null }
