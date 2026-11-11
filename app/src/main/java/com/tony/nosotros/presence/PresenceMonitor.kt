package com.tony.nosotros.presence

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import com.tony.nosotros.data.Feature
import com.tony.nosotros.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Reuses the visible location service. No accessibility, content capture, or usage history upload. */
class PresenceMonitor(private val context: Context, private val prefs: Prefs, private val scope: CoroutineScope,
                      private val pair: String, private val uid: String) {
    private var job: Job? = null
    private var inactiveSince = 0L
    private var cursor = 0L
    private val resumed = mutableMapOf<Pair<String, String>, Long>()
    private val labels = mutableMapOf<String, String>()
    private val lastValue = arrayOf<String?>(null, null)
    private val sentAt = longArrayOf(0, 0)

    fun start() {
        job = scope.launch {
            while (true) {
                runCatching { tick() }
                delay(30_000)
            }
        }
    }
    fun refresh() { job?.cancel(); start() }
    fun stop() { job?.cancel() }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val interactive = context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isDeviceLocked
        val appEnabled = prefs.shareCurrentApp && prefs.featureEnabled(Feature.APP_PRESENCE) && UsagePermission.granted(context)
        val sleepEnabled = prefs.shareSleep && prefs.featureEnabled(Feature.SLEEP_STATUS)
        val label = if (appEnabled && interactive) currentLabel(now) else {
            resumed.clear(); cursor = now; ""
        }
        send(false, appEnabled, label, "idle", 0, now)
        if (!sleepEnabled || interactive) inactiveSince = 0
        else if (inactiveSince == 0L) inactiveSince = now
        val sleep = SleepEstimate.estimate(now, inactiveSince, interactive, prefs.sleepStartHour, prefs.wakeHour)
        send(true, sleepEnabled, "", sleep.state, sleep.since, now)
    }

    private suspend fun send(sleep: Boolean, enabled: Boolean, label: String, state: String, since: Long, now: Long) {
        val i = if (sleep) 1 else 0
        val value = if (!enabled) "off" else "$label|$state|$since"
        val heartbeat = if (sleep || !enabled) 5 * 60_000L else 120_000L
        if (value == lastValue[i] && (value == "off" || now - sentAt[i] < heartbeat)) return
        // Await server acknowledgment, so an offline queued write is not shown as fresh data.
        if (enabled) PresenceRepo.publish(pair, uid, sleep, label, state, since).await()
        else PresenceRepo.clear(pair, uid, sleep).await()
        lastValue[i] = value; sentAt[i] = now
    }

    private fun currentLabel(now: Long): String {
        val start = if (cursor in (now - 120_000)..now) cursor else now - 120_000
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, now)
            ?: return ""
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val key = event.packageName.orEmpty() to event.className.orEmpty()
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumed[key] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> resumed.remove(key)
            }
        }
        cursor = now
        val packageName = resumed.maxByOrNull { it.value }?.key?.first ?: return ""
        if (packageName == "com.android.systemui") return ""
        return labels.getOrPut(packageName) {
            runCatching { val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString().take(100)
            }.getOrDefault("")
        }
    }
}
