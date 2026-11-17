package com.tony.nosotros.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.tony.nosotros.App
import com.tony.nosotros.data.Repo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class InstallStage { CONNECTING, DOWNLOADING, VERIFYING, PREPARING, CONFIRMATION, INSTALLING, INSTALLED, FAILED, CANCELLED }

data class InstallSnapshot(
    val version: String, val stage: InstallStage = InstallStage.CONNECTING,
    val bytes: Long = 0, val total: Long = 0, val bytesPerSecond: Long = 0,
    val sessionId: Int = -1, val message: String = ""
) {
    val percent: Int? get() = if (stage == InstallStage.DOWNLOADING && total > 0)
        ((bytes.coerceIn(0, total) * 100) / total).toInt() else null
    val secondsLeft: Long? get() = if (stage == InstallStage.DOWNLOADING && bytesPerSecond > 0 && total > bytes)
        (total - bytes) / bytesPerSecond else null
    val cancellable: Boolean get() = stage in setOf(InstallStage.CONNECTING, InstallStage.DOWNLOADING, InstallStage.VERIFYING, InstallStage.PREPARING)
    val terminal: Boolean get() = stage in setOf(InstallStage.INSTALLED, InstallStage.FAILED, InstallStage.CANCELLED)
}

/** One application-owned download survives navigation. Only phase/one-second snapshots go to disk. */
object InstallProgress {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var task: Deferred<Boolean>? = null
    private val mutable = MutableStateFlow<InstallSnapshot?>(null)
    val state = mutable.asStateFlow()
    val expanded = MutableStateFlow(true)
    private var restored = false
    private var savedAt = 0L
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("installation", Context.MODE_PRIVATE)

    @Synchronized fun start(ctx: Context, release: Updater.Release): Deferred<Boolean> {
        restore(ctx)
        task?.takeIf { it.isActive }?.let { expanded.value = true; return it }
        mutable.value?.takeIf { !it.terminal && !it.cancellable }?.let {
            expanded.value = true
            return CompletableDeferred(it.version == release.version)
        }
        expanded.value = true
        App.instance.prefs.pendingUpdateConsent = null
        UpdateConsent.pending = null
        set(ctx, InstallSnapshot(release.version))
        return scope.async(start = CoroutineStart.LAZY) { Updater.performInstall(ctx.applicationContext, release) }
            .also { task = it; it.start() }
    }

    @Synchronized fun set(ctx: Context, value: InstallSnapshot) {
        mutable.value = value
        val now = android.os.SystemClock.elapsedRealtime()
        if (value.stage != InstallStage.DOWNLOADING || now - savedAt >= 1000 || value.bytes == value.total) {
            savedAt = now
            prefs(ctx).edit().putString("state", JSONObject().put("version", value.version)
                .put("stage", value.stage.name).put("bytes", value.bytes).put("total", value.total)
                .put("session", value.sessionId).put("message", value.message).toString()).apply()
        }
    }
    fun phase(ctx: Context, stage: InstallStage, message: String = "") {
        mutable.value?.let { set(ctx, it.copy(stage = stage, message = message, bytesPerSecond = 0)) }
    }
    @Synchronized fun restore(ctx: Context) {
        if (!restored) {
            restored = true
            mutable.value = runCatching {
                val j = JSONObject(prefs(ctx).getString("state", null) ?: return@runCatching null)
                InstallSnapshot(j.getString("version"), InstallStage.valueOf(j.getString("stage")),
                    j.optLong("bytes"), j.optLong("total"), sessionId = j.optInt("session", -1), message = j.optString("message"))
            }.getOrNull()
        }
        val s = mutable.value ?: return
        if (!Repo.authorizedSession) return
        // PackageManager, not a completed download or commit, proves that installation succeeded.
        @Suppress("DEPRECATION")
        val installed = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
        if (!Updater.newer(s.version, installed)) {
            set(ctx, s.copy(stage = InstallStage.INSTALLED, message = "", bytesPerSecond = 0))
            Updater.finishInstallAttempt()
            return
        }
        if (s.terminal || task?.isActive == true) return
        val session = if (s.sessionId >= 0) ctx.packageManager.packageInstaller.getSessionInfo(s.sessionId) else null
        when {
            !s.cancellable && session != null && session.appPackageName == ctx.packageName -> {
                val consent = App.instance.prefs.pendingUpdateConsent != null
                set(ctx, s.copy(stage = if (consent) InstallStage.CONFIRMATION else InstallStage.INSTALLING))
            }
            else -> {
                if (session != null) runCatching { ctx.packageManager.packageInstaller.abandonSession(s.sessionId) }
                Updater.finishInstallAttempt()
                set(ctx, s.copy(stage = InstallStage.FAILED, message = "El proceso se interrumpió. Puedes volver a descargar la actualización."))
            }
        }
    }
    @Synchronized fun cancel() {
        if (mutable.value?.cancellable == true) task?.cancel()
    }
    @Synchronized fun dismiss(ctx: Context) {
        if (mutable.value?.terminal == true) {
            mutable.value = null
            prefs(ctx).edit().remove("state").apply()
        }
    }
    fun retry(ctx: Context) {
        val s = mutable.value ?: return
        if (!s.terminal || !Repo.authorizedSession) return
        // Resolve the permitted channel again; retry cannot bypass the owner's release decision.
        scope.launch {
            val release = runCatching { Updater.target(App.instance.prefs.pairId) }.getOrNull()
            if (release != null) start(ctx, release)
            else phase(ctx, InstallStage.FAILED, "Comprueba tu conexión y la versión disponible en Administración o Buscar actualizaciones.")
        }
    }
    fun confirm(ctx: Context) {
        if (!Repo.authorizedSession || mutable.value?.stage != InstallStage.CONFIRMATION) return
        val intent = UpdateConsent.pending ?: App.instance.prefs.pendingUpdateConsent?.let {
            runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull()
        }
        if (intent == null) {
            phase(ctx, InstallStage.FAILED, "La confirmación caducó. Vuelve a descargar la actualización.")
            Updater.finishInstallAttempt()
            return
        }
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
            phase(ctx, InstallStage.CONFIRMATION, "No se pudo abrir Android. Inténtalo de nuevo.")
        }
    }
    fun result(ctx: Context, status: Int, version: String) {
        restore(ctx)
        if (mutable.value?.version != version) return
        if (status == PackageInstaller.STATUS_SUCCESS) {
            // The replacing package may have a new process; reconciliation checks its actual version.
            restore(ctx)
            if (mutable.value?.stage != InstallStage.INSTALLED) phase(ctx, InstallStage.INSTALLING)
        } else if (mutable.value?.stage != InstallStage.INSTALLED) {
            Updater.finishInstallAttempt()
            phase(ctx, InstallStage.FAILED, if (status == PackageInstaller.STATUS_FAILURE_ABORTED)
                "Android canceló la instalación. Puedes volver a intentarlo."
                else "Android no pudo instalar la actualización. Revisa el espacio y el permiso para instalar aplicaciones.")
        }
    }
}
