package com.tony.nosotros.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.tony.nosotros.data.Repo
import android.widget.RemoteViews
import com.tony.nosotros.App
import com.tony.nosotros.R
import com.tony.nosotros.data.Mode
import com.tony.nosotros.ui.MainActivity
import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.TimeFmt
import com.tony.nosotros.util.Together

object Widgets {
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        mgr.getAppWidgetIds(ComponentName(ctx, DistanceWidget::class.java)).forEach { DistanceWidget.update(ctx, mgr, it) }
        mgr.getAppWidgetIds(ComponentName(ctx, TogetherWidget::class.java)).forEach { TogetherWidget.update(ctx, mgr, it) }
        mgr.getAppWidgetIds(ComponentName(ctx, CompactDistanceWidget::class.java)).forEach { CompactWidgets.update(ctx, mgr, it, false) }
        mgr.getAppWidgetIds(ComponentName(ctx, DaysWidget::class.java)).forEach { CompactWidgets.update(ctx, mgr, it, true) }
    }

    internal fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 10, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun signedIn() = App.firebaseConfigured && Repo.authorizedSession
    internal fun pink(ctx: Context) = (ctx.applicationContext as App).prefs.theme == "pink"
}

/** Widget: a qué distancia estamos. */
class DistanceWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { update(ctx, mgr, it) }

    companion object {
        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val p = (ctx.applicationContext as App).prefs
            val pink = Widgets.pink(ctx)
            val v = RemoteViews(ctx.packageName, if (pink) R.layout.widget_distance_pink else R.layout.widget_distance)
            val name = p.partnerName.ifBlank { "Tu pareja" }
            val dist = distanceWidgetValue(Widgets.signedIn(), p.partnerPaused, p.partnerUpdated, p.myLat, p.myLng, p.partnerLat, p.partnerLng)
            v.setTextViewText(R.id.w_title, "Distancia con $name")
            v.setTextViewText(R.id.w_value, dist)
            val mode = Mode.of(p.partnerMode)
            val detail = when {
                !Widgets.signedIn() -> "Abre Nosotros para entrar"
                p.partnerPaused -> "Ubicación en pausa"
                p.partnerUpdated == 0L -> "Sin datos todavía"
                p.partnerPlace != null -> "En ${p.partnerPlace} · ${TimeFmt.ago(p.partnerUpdated)}"
                mode.moving -> "${mode.label} · ${TimeFmt.ago(p.partnerUpdated)}"
                else -> TimeFmt.ago(p.partnerUpdated)
            }
            v.setTextViewText(R.id.w_detail, detail)
            v.setOnClickPendingIntent(R.id.w_root, Widgets.openApp(ctx))
            mgr.updateAppWidget(id, v)
        }
    }
}

/** Widget: cuánto tiempo llevamos juntos. */
class TogetherWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { update(ctx, mgr, it) }

    companion object {
        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val p = (ctx.applicationContext as App).prefs
            val pink = Widgets.pink(ctx)
            val v = RemoteViews(ctx.packageName, if (pink) R.layout.widget_together_pink else R.layout.widget_together)
            val s = if (Widgets.signedIn()) Together.span(p.since) else null
            if (s == null) {
                v.setTextViewText(R.id.w_title, "Juntos")
                v.setTextViewText(R.id.w_value, "♥")
                v.setTextViewText(R.id.w_detail, "Pon su fecha de aniversario en Ajustes")
            } else {
                v.setTextViewText(R.id.w_title, "Juntos hace")
                v.setTextViewText(R.id.w_value, "${s.totalDays} días")
                val next = Together.daysToAnniversary(p.since)
                val extra = when (next) {
                    0 -> "¡Feliz aniversario!"
                    null -> ""
                    else -> "Aniversario en $next días"
                }
                v.setTextViewText(R.id.w_detail, "${Together.longText(s)} · ${s.hours} h\n$extra")
            }
            v.setOnClickPendingIntent(R.id.w_root, Widgets.openApp(ctx))
            mgr.updateAppWidget(id, v)
        }
    }
}


/** Versiones pequeñas para hosts de pantalla de bloqueo y LockStar. */
class CompactDistanceWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { CompactWidgets.update(ctx, mgr, it, false) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) = CompactWidgets.update(ctx, mgr, id, false)
}
class DaysWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { CompactWidgets.update(ctx, mgr, it, true) }
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) = CompactWidgets.update(ctx, mgr, id, true)
}
internal object CompactWidgets {
    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, days: Boolean) {
        val p = (ctx.applicationContext as App).prefs
        val v = RemoteViews(ctx.packageName, R.layout.widget_compact)
        v.setTextViewText(R.id.w_title, if (days) "Días juntos" else "Distancia")
        val span = if (Widgets.signedIn()) Together.span(p.since) else null
        val value = if (days) when {
            !Widgets.signedIn() -> "Inicia sesión"
            span == null -> "Sin fecha"
            else -> "${span.totalDays} días"
        } else distanceWidgetValue(Widgets.signedIn(), p.partnerPaused, p.partnerUpdated, p.myLat, p.myLng, p.partnerLat, p.partnerLng)
        v.setTextViewText(R.id.w_value, value)
        v.setTextViewText(R.id.w_detail, if (!Widgets.signedIn()) "Nosotros" else if (days) "♥" else if (p.partnerPaused) "En pausa" else if (p.partnerUpdated > 0) TimeFmt.ago(p.partnerUpdated) else "Esperando señal")
        v.setTextColor(R.id.w_value, if (days) 0xFFF9A8D4.toInt() else 0xFF5EEAD4.toInt())
        v.setOnClickPendingIntent(R.id.w_root, Widgets.openApp(ctx))
        mgr.updateAppWidget(id, v)
    }
}
