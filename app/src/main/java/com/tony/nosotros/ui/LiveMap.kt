package com.tony.nosotros.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.PathParser
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tony.nosotros.data.Mode
import com.tony.nosotros.data.Place
import com.tony.nosotros.util.Pt
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Vista satélite (Esri World Imagery) con nombres de calles y lugares encima. */
private const val SATELLITE_STYLE = """{"version":8,"sources":{
"sat":{"type":"raster","tileSize":256,"maxzoom":19,"attribution":"Esri, Maxar, Earthstar Geographics",
"tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"]},
"roads":{"type":"raster","tileSize":256,"maxzoom":19,
"tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}"]},
"names":{"type":"raster","tileSize":256,"maxzoom":19,
"tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"]}},
"layers":[{"id":"sat","type":"raster","source":"sat"},{"id":"roads","type":"raster","source":"roads","paint":{"raster-opacity":0.7}},
{"id":"names","type":"raster","source":"names"}]}"""

private val OUR_LAYERS = setOf("route-glow", "route-line", "place-area", "place-label", "people-halo", "people-icon")

data class MapPerson(
    val uid: String, val initial: String, val color: Int, val ink: Int,
    val lat: Double, val lng: Double, val mode: Mode, val paused: Boolean, val speed: Int = 0, val avatar: String = "",
) {
    val inMotion get() = !paused && mode.moving && speed >= 3
}

data class MapRoute(val id: String, val points: List<Pt>, val color: Int)

@Composable
fun LiveMap(
    modifier: Modifier = Modifier,
    people: List<MapPerson>,
    places: List<Place> = emptyList(),
    routes: List<MapRoute> = emptyList(),
    /** Cambia este valor para volver a encuadrar la cámara. */
    fitKey: Any? = Unit,
    /** Puntos a encuadrar; null = todo lo visible. */
    focus: List<Pt>? = null,
    satellite: Boolean = false,
    threeD: Boolean = false,
    topInsetPx: Int = 0,
    bottomInsetPx: Int = 0,
    initialCenter: Pt? = null,
    onLongPress: ((Double, Double) -> Unit)? = null,
    onPersonTap: ((String) -> Unit)? = null,
    onPlaceTap: ((String) -> Unit)? = null,
    onMapTap: (() -> Unit)? = null,
    /** Centro de la cámara cada vez que el mapa se detiene (para elegir un punto exacto). */
    onCenter: ((Double, Double) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val palette = C.p
    val mapView = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val longPress by rememberUpdatedState(onLongPress)
    val personTap by rememberUpdatedState(onPersonTap)
    val placeTap by rememberUpdatedState(onPlaceTap)
    val mapTap by rememberUpdatedState(onMapTap)
    val center by rememberUpdatedState(onCenter)

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    AndroidView(modifier = modifier, factory = {
        mapView.getMapAsync { m ->
            val d = ctx.resources.displayMetrics.density
            m.uiSettings.apply {
                isCompassEnabled = true
                compassGravity = Gravity.TOP or Gravity.END
                setCompassMargins(0, topInsetPx + (8 * d).toInt(), (20 * d).toInt(), 0)
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isZoomGesturesEnabled = true
                isDoubleTapGesturesEnabled = true
                logoGravity = Gravity.BOTTOM or Gravity.START
                attributionGravity = Gravity.BOTTOM or Gravity.START
                setLogoMargins((12 * d).toInt(), 0, 0, bottomInsetPx + (6 * d).toInt())
                setAttributionMargins((100 * d).toInt(), 0, 0, bottomInsetPx + (6 * d).toInt())
            }
            m.setMaxZoomPreference(19.5)
            initialCenter?.let { m.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                .target(LatLng(it.lat, it.lng)).zoom(16.0).build() }
            m.addOnMapLongClickListener { ll -> longPress?.invoke(ll.latitude, ll.longitude); longPress != null }
            m.addOnMapClickListener { ll ->
                val pt = m.projection.toScreenLocation(ll)
                val r = 22 * d
                val box = RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r)
                val person = m.queryRenderedFeatures(box, "people-icon").firstOrNull()?.getStringProperty("uid")
                if (person != null) { personTap?.invoke(person); return@addOnMapClickListener true }
                val place = m.queryRenderedFeatures(box, "place-label").firstOrNull()?.getStringProperty("pid")
                if (place != null) { placeTap?.invoke(place); return@addOnMapClickListener true }
                mapTap?.invoke(); false
            }
            m.addOnCameraIdleListener {
                val t = m.cameraPosition.target ?: return@addOnCameraIdleListener
                center?.invoke(t.latitude, t.longitude)
            }
            map = m
        }
        mapView
    })

    // Cargar (o recargar) el estilo cuando cambian el tema o la vista satélite
    LaunchedEffect(map, palette.id, satellite) {
        val m = map ?: return@LaunchedEffect
        style = null
        val builder = if (satellite) Style.Builder().fromJson(SATELLITE_STYLE) else Style.Builder().fromUri(palette.mapStyle)
        m.setStyle(builder) { s ->
            if (!satellite) tintStyle(s, palette)
            if (!satellite) setupBuildings(s, palette)
            setupLayers(s)
            style = s
        }
    }

    val tilt3d = threeD && LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.MAP_3D)
    val showPhotos = LocalFeatures.current.enabled(com.tony.nosotros.data.Feature.PROFILE_PHOTO)
    LaunchedEffect(style, tilt3d) {
        style?.getLayer("nosotros-buildings-3d")?.setProperties(
            PropertyFactory.visibility(if (tilt3d) Property.VISIBLE else Property.NONE)
        )
    }
    LaunchedEffect(map, tilt3d) {
        val m = map ?: return@LaunchedEffect
        if (kotlin.math.abs(m.cameraPosition.tilt - (if (tilt3d) 55.0 else 0.0)) > 1.0) {
            m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition)
                .tilt(if (tilt3d) 55.0 else 0.0).build()), 450)
        }
    }

    LaunchedEffect(style, people, places, routes, showPhotos) {
        val s = style ?: return@LaunchedEffect
        updateData(ctx, s, palette, if (showPhotos) people else people.map { it.copy(avatar = "") }, places, routes)
    }

    // Pulso animado alrededor de quien se está moviendo
    val anyMoving = people.any { it.inMotion }
    LaunchedEffect(style, anyMoving) {
        val halo = style?.getLayer("people-halo") ?: return@LaunchedEffect
        val isMoving = Expression.eq(Expression.get("moving"), Expression.literal(true))
        if (!anyMoving) {
            halo.setProperties(PropertyFactory.circleRadius(32f), PropertyFactory.circleOpacity(0.22f))
            return@LaunchedEffect
        }
        var t = 0
        while (true) {
            val ph = (t % 20) / 20f
            halo.setProperties(
                PropertyFactory.circleRadius(Expression.switchCase(isMoving, Expression.literal(26f + 30f * ph), Expression.literal(32f))),
                PropertyFactory.circleOpacity(Expression.switchCase(isMoving, Expression.literal(0.45f * (1f - ph)), Expression.literal(0.22f))),
            )
            t++
            kotlinx.coroutines.delay(70)
        }
    }

    var fitted by remember { mutableStateOf<Any?>(if (initialCenter != null) fitKey else null) }
    LaunchedEffect(map, fitKey, people.isNotEmpty(), routes.isNotEmpty()) {
        val m = map ?: return@LaunchedEffect
        val pts = focus?.map { LatLng(it.lat, it.lng) }
            ?: (people.map { LatLng(it.lat, it.lng) } + routes.flatMap { r -> r.points.map { LatLng(it.lat, it.lng) } })
        if (pts.isEmpty() || fitted == fitKey) return@LaunchedEffect
        fitted = fitKey
        val pad = (64 * ctx.resources.displayMetrics.density).toInt()
        if (pts.size == 1 || pts.all { it.distanceTo(pts[0]) < 50 }) {
            m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition)
                .target(pts[0]).zoom(16.0).tilt(if (tilt3d) 55.0 else 0.0).build()), 800)
        } else {
            val b = LatLngBounds.Builder().includes(pts).build()
            val camera = m.getCameraForLatLngBounds(b, intArrayOf(pad, topInsetPx + pad, pad, bottomInsetPx + pad),
                m.cameraPosition.bearing, if (tilt3d) 55.0 else 0.0)
            if (camera != null) m.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 800)
        }
    }
}

private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

/** Ajusta los colores del mapa base para que combinen con el tema y se lean bien. */
private fun tintStyle(s: Style, p: Palette) {
    for (l in s.layers) {
        val id = l.id.lowercase()
        if (l.id in OUR_LAYERS) continue
        runCatching {
            when (l) {
                is SymbolLayer -> if (p.dark) l.setProperties(
                    PropertyFactory.textColor(if (id.contains("road") || id.contains("street")) "#D5DCE6" else "#F1F5F9"),
                    PropertyFactory.textHaloColor("#0B0F14"), PropertyFactory.textHaloWidth(1.6f),
                ) else l.setProperties(
                    PropertyFactory.textColor("#4A1530"), PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.6f),
                )
                is BackgroundLayer -> if (!p.dark) l.setProperties(PropertyFactory.backgroundColor("#FFF6FA"))
                is FillLayer -> when {
                    id.contains("water") -> l.setProperties(PropertyFactory.fillColor(if (p.dark) "#0F2B40" else "#F8CFE0"))
                    id.contains("park") || id.contains("green") || id.contains("wood") || id.contains("grass") ->
                        l.setProperties(PropertyFactory.fillColor(if (p.dark) "#11291E" else "#E7F3E6"))
                    id.contains("building") && p.dark -> l.setProperties(PropertyFactory.fillColor("#1A212B"))
                    id.contains("landuse") && !p.dark -> l.setProperties(PropertyFactory.fillColor("#FBEAF1"))
                }
                is LineLayer -> if (p.dark && (id.contains("road") || id.contains("highway") || id.contains("street") ||
                        id.contains("bridge") || id.contains("tunnel"))) {
                    val major = id.contains("major") || id.contains("motorway") || id.contains("trunk") || id.contains("primary")
                    l.setProperties(PropertyFactory.lineColor(if (major) "#45546A" else "#313C4B"))
                }
            }
        }
    }
}

/** Usa la fuente vectorial del estilo; los estilos antiguos CARTO y los actuales difieren. */
private fun setupBuildings(s: Style, palette: Palette) {
    val footprint = s.layers.filterIsInstance<FillLayer>().firstOrNull {
        it.id.contains("building", ignoreCase = true) && s.getSource(it.sourceId) is VectorSource
    } ?: return
    val sourceLayer = footprint.sourceLayer ?: return
    val buildings = FillExtrusionLayer("nosotros-buildings-3d", footprint.sourceId).apply {
        this.sourceLayer = sourceLayer
        minZoom = 14f
        setProperties(
            PropertyFactory.fillExtrusionHeight(Expression.coalesce(Expression.get("render_height"), Expression.get("height"), Expression.literal(8))),
            PropertyFactory.fillExtrusionBase(Expression.coalesce(Expression.get("render_min_height"), Expression.get("min_height"), Expression.literal(0))),
            PropertyFactory.fillExtrusionColor(if (palette.dark) "#536579" else "#D6AFC2"),
            PropertyFactory.fillExtrusionOpacity(0.85f),
            PropertyFactory.visibility(Property.NONE),
        )
    }
    val label = s.layers.firstOrNull { it is SymbolLayer }?.id
    if (label != null) s.addLayerBelow(buildings, label) else s.addLayer(buildings)
}

private fun setupLayers(s: Style) {
    s.addSource(GeoJsonSource("routes"))
    s.addSource(GeoJsonSource("places"))
    s.addSource(GeoJsonSource("people"))
    val color = Expression.toColor(Expression.get("color"))
    s.addLayer(
        LineLayer("route-glow", "routes").withProperties(
            PropertyFactory.lineColor(color), PropertyFactory.lineWidth(12f),
            PropertyFactory.lineBlur(8f), PropertyFactory.lineOpacity(0.45f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    s.addLayer(
        LineLayer("route-line", "routes").withProperties(
            PropertyFactory.lineColor(color), PropertyFactory.lineWidth(4.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    // Círculo del radio real del lugar (en metros, escala con el zoom)
    s.addLayer(
        CircleLayer("place-area", "places").withProperties(
            PropertyFactory.circleColor(color), PropertyFactory.circleOpacity(0.10f),
            PropertyFactory.circleStrokeColor(color), PropertyFactory.circleStrokeOpacity(0.55f),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.exponential(2), Expression.zoom(),
                    Expression.stop(10, Expression.division(Expression.get("r"), Expression.literal(150f))),
                    Expression.stop(20, Expression.product(Expression.get("r"), Expression.literal(6.8f))),
                )
            ),
        )
    )
    s.addLayer(
        SymbolLayer("place-label", "places").withProperties(
            PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true), PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        )
    )
    s.addLayer(
        CircleLayer("people-halo", "people").withProperties(
            PropertyFactory.circleColor(color), PropertyFactory.circleOpacity(0.22f), PropertyFactory.circleRadius(32f),
        )
    )
    s.addLayer(
        SymbolLayer("people-icon", "people").withProperties(
            PropertyFactory.iconImage(Expression.get("icon")), PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
    )
}

private fun updateData(ctx: Context, s: Style, pal: Palette, people: List<MapPerson>, places: List<Place>, routes: List<MapRoute>) {
    (s.getSource("routes") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(
        routes.filter { it.points.size >= 2 }.map { r ->
            Feature.fromGeometry(LineString.fromLngLats(r.points.map { Point.fromLngLat(it.lng, it.lat) })).apply {
                addStringProperty("color", hex(r.color))
            }
        }
    ))

    (s.getSource("places") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(
        places.filter { !it.suggested && it.name.isNotBlank() }.map { p ->
            val id = "pl_${pal.id}_${p.name}"
            if (s.getImage(id) == null) s.addImage(id, labelBitmap(ctx, pal, p.name))
            Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                addStringProperty("icon", id)
                addStringProperty("pid", p.id)
                addStringProperty("color", hex(pal.me.toArgb()))
                addNumberProperty("r", p.radius)
            }
        }
    ))

    (s.getSource("people") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(
        people.map { p ->
            val id = "av_${pal.id}_${p.uid}_${p.mode}_${p.paused}_${p.initial}_${p.color}_${p.avatar.hashCode()}_${if (p.inMotion) p.speed else 0}"
            if (s.getImage(id) == null) s.addImage(id, avatarBitmap(ctx, pal, p))
            Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                addStringProperty("icon", id)
                addStringProperty("uid", p.uid)
                addBooleanProperty("moving", p.inMotion)
                addStringProperty("color", hex(if (p.paused) 0x6B7280 else p.color))
            }
        }
    ))
}

/** Avatar: quieto = inicial + insignia; en movimiento = vehículo grande + velocidad debajo. */
private fun avatarBitmap(ctx: Context, pal: Palette, p: MapPerson): Bitmap {
    val d = ctx.resources.displayMetrics.density
    val w = (72 * d).toInt()
    val h = (100 * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.density = ctx.resources.displayMetrics.densityDpi
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val cx = w / 2f
    val cy = h / 2f
    val r = 22 * d
    val bg = if (p.paused) 0xFF6B7280.toInt() else p.color
    val ink = if (p.paused) 0xFFFFFFFF.toInt() else p.ink
    paint.color = 0xFFFFFFFF.toInt(); c.drawCircle(cx, cy, r + 3 * d, paint)
    paint.color = bg; c.drawCircle(cx, cy, r, paint)

    fun icon(paths: List<String>, x: Float, y: Float, sizeDp: Float, color: Int) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.4f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        c.save()
        c.translate(x - sizeDp * d / 2, y - sizeDp * d / 2); c.scale(sizeDp * d / 24f, sizeDp * d / 24f)
        paths.forEach { runCatching { c.drawPath(PathParser.createPathFromPathData(it), stroke) } }
        c.restore()
    }
    fun letter(x: Float, y: Float, size: Float, color: Int) {
        paint.style = Paint.Style.FILL; paint.color = color
        paint.textSize = size * d; paint.typeface = bold; paint.textAlign = Paint.Align.CENTER
        c.drawText(p.initial, x, y - (paint.descent() + paint.ascent()) / 2, paint)
    }
    fun badge(x: Float, y: Float) {
        paint.style = Paint.Style.FILL; paint.color = pal.surface.toArgb(); c.drawCircle(x, y, 12 * d, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d; paint.color = bg
        c.drawCircle(x, y, 12 * d, paint)
    }
    val bx = cx + r * 0.8f
    val by = cy - r * 0.8f

    if (p.inMotion) {
        icon(p.mode.paths(), cx, cy, 30f, ink)
        badge(bx, by); letter(bx, by, 12f, pal.text.toArgb())
        // píldora con la velocidad
        val txt = "${p.speed} km/h"
        paint.style = Paint.Style.FILL; paint.textSize = 11.5f * d; paint.typeface = bold; paint.textAlign = Paint.Align.CENTER
        val tw = paint.measureText(txt)
        val pill = RectF(cx - tw / 2 - 7 * d, cy + r + 5 * d, cx + tw / 2 + 7 * d, cy + r + 24 * d)
        paint.color = pal.surface.toArgb(); c.drawRoundRect(pill, 10 * d, 10 * d, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d; paint.color = bg
        c.drawRoundRect(pill, 10 * d, 10 * d, paint)
        paint.style = Paint.Style.FILL; paint.color = pal.text.toArgb()
        c.drawText(txt, cx, pill.centerY() - (paint.descent() + paint.ascent()) / 2, paint)
    } else {
        val photo = decodeAvatar(p.avatar)
        if (photo == null) letter(cx, cy, 18f, ink) else {
            c.save()
            c.clipPath(android.graphics.Path().apply { addCircle(cx, cy, r, android.graphics.Path.Direction.CW) })
            c.drawBitmap(photo, null, RectF(cx - r, cy - r, cx + r, cy + r), paint)
            c.restore()
        }
        badge(bx, by)
        icon(if (p.paused) Ic.P_PAUSE else p.mode.paths(), bx, by, 15f, if (p.paused) 0xFF6B7280.toInt() else pal.text.toArgb())
    }
    return bmp
}

/** Etiqueta tipo "píldora" con el nombre del lugar y un punto debajo. */
private fun labelBitmap(ctx: Context, pal: Palette, text: String): Bitmap {
    val d = ctx.resources.displayMetrics.density
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * d; color = pal.text.toArgb()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val tw = tp.measureText(text)
    val w = (tw + 24 * d).toInt()
    val h = (44 * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.density = ctx.resources.displayMetrics.densityDpi
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val pill = RectF(1f, 1f, w - 1f, 27 * d)
    p.color = pal.surface.toArgb(); c.drawRoundRect(pill, 9 * d, 9 * d, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = pal.me.toArgb()
    c.drawRoundRect(pill, 9 * d, 9 * d, p)
    c.drawText(text, 12 * d, 14 * d - (tp.descent() + tp.ascent()) / 2, tp)
    p.style = Paint.Style.FILL; p.color = pal.surface.toArgb()
    c.drawCircle(w / 2f, h - 8 * d, 6.5f * d, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 3f * d; p.color = pal.me.toArgb()
    c.drawCircle(w / 2f, h - 8 * d, 5.5f * d, p)
    return bmp
}
