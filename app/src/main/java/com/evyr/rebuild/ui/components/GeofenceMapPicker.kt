package com.evyr.rebuild.ui.components

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Interactive geofence editor: drag the pin to move the location, drag the edge
 * of the circle to resize the radius. Built on osmdroid (OpenStreetMap tiles) --
 * deliberately not Google Maps, which would require the user to register for
 * their own API key and set up billing before this screen would even render.
 * osmdroid needs neither.
 *
 * osmdroid's built-in Polygon overlay can render a circle but has no built-in
 * "drag the edge to resize" interaction, so that part is hand-rolled: a
 * transparent Overlay intercepts touch events, and a drag is classified as an
 * edge-resize if it starts near the circle's current edge, or a pin-move if it
 * starts near the marker -- otherwise the gesture passes through to the map's
 * own pan/zoom handling.
 */
@Composable
fun GeofenceMapPicker(
    initialLat: Double,
    initialLng: Double,
    radiusMeters: Float,
    onChange: (lat: Double, lng: Double, radiusMeters: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // The circle is drawn inside the AndroidView factory, which Compose runs
    // exactly once. Without a way back in, a radius typed outside the map never
    // reached it and the circle stayed whatever size it started at. This handle
    // lets the update block push a new radius into that closure.
    val handle = remember { MapPickerHandle() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            // osmdroid persists tile-cache config via SharedPreferences and reads a
            // user-agent string; skipping this setup causes tile requests to get
            // blocked by OSM's servers as looking like an unidentified bot.
            // Uses a plain named SharedPreferences rather than the deprecated
            // android.preference.PreferenceManager.getDefaultSharedPreferences.
            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid_prefs", android.content.Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = ctx.packageName

            val mapView = MapView(ctx)
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(16.0)

            var center = GeoPoint(initialLat, initialLng)
            var currentRadius = radiusMeters
            mapView.controller.setCenter(center)

            val marker = Marker(mapView).apply {
                position = center
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                // Dragging is handled by the custom overlay below, not osmdroid's
                // own marker-drag, so both the pin and the circle stay in sync
                // through one single source of truth (`center`) instead of two
                // independently-moving pieces.
                isDraggable = false
            }
            mapView.overlays.add(marker)

            val circle = Polygon(mapView).apply {
                fillPaint.color = AndroidColor.argb(40, 255, 204, 0)
                outlinePaint.color = AndroidColor.rgb(255, 204, 0)
                outlinePaint.strokeWidth = 4f
            }
            mapView.overlays.add(circle)

            fun metersToDegreesLat(m: Float): Double = m / 111_320.0
            fun metersToDegreesLng(m: Float, atLat: Double): Double =
                m / (111_320.0 * cos(Math.toRadians(atLat)))

            fun redrawCircle() {
                val points = (0..72).map { i ->
                    val angle = Math.toRadians(i * 5.0)
                    GeoPoint(
                        center.latitude + metersToDegreesLat(currentRadius) * kotlin.math.cos(angle),
                        center.longitude + metersToDegreesLng(currentRadius, center.latitude) * kotlin.math.sin(angle)
                    )
                }
                circle.points = points
            }
            redrawCircle()

            handle.lastPushed = currentRadius
            handle.setRadius = { newRadius ->
                currentRadius = newRadius
                redrawCircle()
                mapView.invalidate()
            }

            var dragMode = 0 // 0 = none, 1 = moving pin, 2 = resizing radius

            val touchOverlay = object : Overlay() {
                override fun onTouchEvent(event: MotionEvent, map: MapView): Boolean {
                    val projection = map.projection
                    val touchGeo = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            val pinScreen = projection.toPixels(center, null)
                            val edgeGeo = GeoPoint(
                                center.latitude + metersToDegreesLat(currentRadius),
                                center.longitude
                            )
                            val edgeScreen = projection.toPixels(edgeGeo, null)
                            val distToPin = Math.hypot((event.x - pinScreen.x).toDouble(), (event.y - pinScreen.y).toDouble())
                            val distToEdge = Math.hypot((event.x - edgeScreen.x).toDouble(), (event.y - edgeScreen.y).toDouble())

                            dragMode = when {
                                distToEdge < 60 -> 2
                                distToPin < 80 -> 1
                                else -> 0
                            }
                            // Consume the event only when actually starting a drag
                            // on the pin or the edge handle -- anywhere else on the
                            // map falls through so normal pan/zoom still works.
                            return dragMode != 0
                        }
                        MotionEvent.ACTION_MOVE -> {
                            when (dragMode) {
                                1 -> {
                                    center = touchGeo
                                    marker.position = center
                                    redrawCircle()
                                    map.invalidate()
                                    return true
                                }
                                2 -> {
                                    val dLat = touchGeo.latitude - center.latitude
                                    val dLng = touchGeo.longitude - center.longitude
                                    val metersPerDegreeLat = 111_320.0
                                    val metersPerDegreeLng = 111_320.0 * cos(Math.toRadians(center.latitude))
                                    val dy = dLat * metersPerDegreeLat
                                    val dx = dLng * metersPerDegreeLng
                                    val newRadius = Math.hypot(dx, dy).roundToInt().coerceIn(25, 2000)
                                    currentRadius = newRadius.toFloat()
                                    handle.lastPushed = currentRadius
                                    redrawCircle()
                                    map.invalidate()
                                    return true
                                }
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (dragMode != 0) {
                                onChange(center.latitude, center.longitude, currentRadius)
                            }
                            dragMode = 0
                        }
                    }
                    return false
                }
            }
            mapView.overlays.add(touchOverlay)

            mapView
        },
        update = {
            // Only push when the value actually differs. Dragging the circle
            // already updates it internally and reports back up, which would
            // otherwise bounce straight back down here as a redundant redraw
            // mid-gesture.
            if (handle.lastPushed != radiusMeters) {
                handle.lastPushed = radiusMeters
                handle.setRadius?.invoke(radiusMeters)
            }
        }
    )
}

/**
 * Bridge between Compose state and the osmdroid view built inside the
 * AndroidView factory. The factory body runs once, so anything it creates --
 * here, the circle overlay and the radius backing it -- is otherwise
 * unreachable afterwards.
 */
private class MapPickerHandle {
    var setRadius: ((Float) -> Unit)? = null
    var lastPushed: Float? = null
}
