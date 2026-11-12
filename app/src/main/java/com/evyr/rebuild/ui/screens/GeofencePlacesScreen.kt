package com.evyr.rebuild.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.data.GeofencePlace
import com.evyr.rebuild.location.GeofenceManager
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.components.HexGridBackground
import com.evyr.rebuild.ui.theme.*
import com.google.android.gms.location.LocationServices

/**
 * "Use my current location" flow — the simplest possible way to set a place without
 * making the user type coordinates. Requires ACCESS_FINE_LOCATION already granted
 * (handled earlier in PermissionFlowScreen); if not granted, shows a message instead
 * of crashing.
 */
/** Radius bounds for a geofence place. The floor is 50m rather than 25m
 *  because GPS accuracy alone is 20-50m outdoors -- a smaller circle is
 *  guaranteed to produce phantom entries and exits. */
private const val RADIUS_MIN_M = 50
private const val RADIUS_MAX_M = 2000
private const val RADIUS_STEP_M = 25

@Composable
fun GeofencePlacesScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by viewModel.geofencePlaces.collectAsState()
    // Label draft starts blank and only defaults to "Home" for the very first place —
    // previously this stayed "Home" forever, so a second/third place silently saved
    // under the same label unless you noticed and retyped it.
    var labelDraft by remember(places.size) { mutableStateOf(if (places.isEmpty()) "Home" else "") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var editingChecklistFor by remember { mutableStateOf<GeofencePlace?>(null) }
    var adjustingOnMapFor by remember { mutableStateOf<GeofencePlace?>(null) }
    var reregisterStatus by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    val checkableDirectives by viewModel.dailyItems.collectAsState()
    val weeklyDirectives by viewModel.weeklyItems.collectAsState()

    val geofenceManager = remember { GeofenceManager(context) }

    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // One reconciliation pass whenever this screen is opened with a settled
    // places list: clears any OS-registered geofence left over from a past
    // removeGeofence() call that failed silently (the "place not found in
    // database" bug -- a deleted place's geofence kept firing because the
    // removal request never actually reached Play Services). Runs quietly;
    // only logged, not surfaced as a status message, since this should be a
    // no-op in the common case where nothing drifted.
    //
    // This used to reconcile against the on-screen `places` list, which starts
    // empty until the database loads -- so every open first ran "remove all,
    // add nothing" (the "0 place(s)" line in the debug log), and backing out
    // before the list loaded left no geofences at all. GeofenceSync reads the
    // database itself, and only re-registers when something needs it.
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            com.evyr.rebuild.location.GeofenceSync.syncIfNeeded(context, "Locations screen")
        }
    }

    HexGridBackground(modifier = Modifier.fillMaxSize().background(NervBackground)) {
    Column(modifier = Modifier.fillMaxSize()) {
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NervGreen)
            }
            Text("LOCATIONS", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 16.sp, letterSpacing = 2.sp)
        }
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(places) { place ->
                PlaceRow(
                    place = place,
                    onDelete = {
                        geofenceManager.removeGeofence(place)
                        viewModel.deleteGeofencePlace(place)
                    },
                    onToggleArrival = { enabled ->
                        scope.launch { viewModel.addGeofencePlace(place.copy(notifyOnArrival = enabled)) }
                    },
                    onToggleDeparture = { enabled ->
                        scope.launch { viewModel.addGeofencePlace(place.copy(notifyOnDeparture = enabled)) }
                    },
                    onEditChecklist = { editingChecklistFor = place },
                    onReregister = {
                        reregisterStatus = reregisterStatus + (place.id to "Re-registering...")
                        geofenceManager.registerGeofence(place) { success, error ->
                            reregisterStatus = reregisterStatus + (place.id to
                                if (success) "Active" else "Failed${error?.let { ": $it" } ?: ""}")
                        }
                    },
                    onAdjustOnMap = { adjustingOnMapFor = place },
                    reregisterStatus = reregisterStatus[place.id]
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            if (!hasLocationPermission) {
                Text(
                    "LOCATION PERMISSION NOT GRANTED — enable it in system settings first",
                    fontFamily = TerminalFontFamily, color = NervRed, fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
            }
            if (places.isNotEmpty()) {
                Text(
                    "Adding place #${places.size + 1} — give it a distinct label (e.g. Work, Gym)",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
                )
                Spacer(Modifier.height(4.dp))
            }
            OutlinedTextField(
                value = labelDraft,
                onValueChange = { labelDraft = it },
                label = { Text("Label", fontFamily = TerminalFontFamily) },
                placeholder = { Text(if (places.isEmpty()) "Home" else "Work, Gym, ...", fontFamily = TerminalFontFamily, color = NervDim) },
                modifier = Modifier.fillMaxWidth(),
                colors = rebuildTextFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            statusMessage?.let {
                Text(it, fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = {
                if (!hasLocationPermission) {
                    statusMessage = "Grant location permission first."
                    return@TextButton
                }
                statusMessage = "Clearing stale geofences..."
                geofenceManager.reconcileGeofences(places) { success, error ->
                    statusMessage = if (success) {
                        "Cleaned up — ${places.size} place(s) registered, nothing stale left."
                    } else {
                        "Cleanup failed: $error"
                    }
                }
            }) {
                Text("Fix stale geofences", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!hasLocationPermission) {
                        statusMessage = "Grant location permission first."
                        return@Button
                    }
                    val effectiveLabel = labelDraft.ifBlank { if (places.isEmpty()) "Home" else null }
                    if (effectiveLabel == null) {
                        statusMessage = "Give this place a label first."
                        return@Button
                    }
                    val fused = LocationServices.getFusedLocationProviderClient(context)
                    try {
                        fused.lastLocation.addOnSuccessListener { loc ->
                            if (loc == null) {
                                statusMessage = "No location fix yet — move somewhere with GPS/network signal and retry."
                                return@addOnSuccessListener
                            }
                            val draft = GeofencePlace(
                                label = effectiveLabel,
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                radiusMeters = RADIUS_MIN_M.toFloat() * 3f  // 150m
                            )
                            // draft.id is 0 here -- Room only assigns the real id on
                            // insert. Registering the geofence with the stale id=0
                            // object was the actual root bug: it registered under
                            // request id "place_0" while the database row got a
                            // different real id, so GeofenceBroadcastReceiver's
                            // lookup by that id would resolve to nothing and the
                            // worker would silently no-op -- looking, from the
                            // Locations screen, exactly like a correctly configured
                            // place that simply never fires.
                            scope.launch {
                                val realId = viewModel.addGeofencePlace(draft)
                                val saved = draft.copy(id = realId)
                                statusMessage = "Registering \"${saved.label}\"..."
                                geofenceManager.registerGeofence(saved) { success, error ->
                                    statusMessage = if (success) {
                                        "Saved \"${saved.label}\" — geofence active."
                                    } else {
                                        "Saved \"${saved.label}\" but the geofence itself failed to register" +
                                            (error?.let { ": $it" } ?: "") +
                                            " — check background location permission."
                                    }
                                }
                            }
                            labelDraft = ""
                        }
                    } catch (e: SecurityException) {
                        statusMessage = "Permission error — re-check location settings."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("USE MY CURRENT LOCATION", fontFamily = TerminalFontFamily)
            }
        }
    }
    }

    editingChecklistFor?.let { place ->
        PlaceChecklistDialog(
            place = place,
            viewModel = viewModel,
            daily = checkableDirectives,
            weekly = weeklyDirectives,
            onDismiss = { editingChecklistFor = null }
        )
    }

    adjustingOnMapFor?.let { place ->
        var pendingLat by remember(place.id) { mutableStateOf(place.latitude) }
        var pendingLng by remember(place.id) { mutableStateOf(place.longitude) }
        var pendingRadius by remember(place.id) { mutableStateOf(place.radiusMeters) }

        Dialog(
            onDismissRequest = { adjustingOnMapFor = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NervBackground)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        place.label.uppercase(),
                        fontFamily = TerminalFontFamily,
                        color = NervOrange,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "R = ${pendingRadius.toInt()}m",
                        fontFamily = TerminalFontFamily,
                        color = NervGreen,
                        fontSize = 12.sp
                    )
                }
                // Typed number with steppers, not a slider. A slider spanning
                // 25-1000m across a phone is roughly 2.5m per pixel, so landing
                // on a specific value is guesswork -- and radius is exactly the
                // setting you want to be precise about.
                var radiusText by remember(place.id) { mutableStateOf(pendingRadius.toInt().toString()) }
                fun applyRadius(meters: Int) {
                    val clamped = meters.coerceIn(RADIUS_MIN_M, RADIUS_MAX_M)
                    pendingRadius = clamped.toFloat()
                    radiusText = clamped.toString()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { applyRadius(pendingRadius.toInt() - RADIUS_STEP_M) }) {
                        Text("\u2212", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 20.sp)
                    }
                    OutlinedTextField(
                        value = radiusText,
                        onValueChange = { raw ->
                            val digits = raw.filter(Char::isDigit).take(4)
                            radiusText = digits
                            // Only push a valid in-range number through to the map;
                            // an empty or out-of-range field stays editable rather
                            // than snapping under you mid-type.
                            digits.toIntOrNull()?.takeIf { it in RADIUS_MIN_M..RADIUS_MAX_M }?.let {
                                pendingRadius = it.toFloat()
                            }
                        },
                        label = { Text("Radius (m)", fontFamily = TerminalFontFamily) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = rebuildTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { applyRadius(pendingRadius.toInt() + RADIUS_STEP_M) }) {
                        Text("+", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 20.sp)
                    }
                }
                Text(
                    "$RADIUS_MIN_M-${RADIUS_MAX_M}m. GPS is only good to 20-50m outdoors and worse " +
                        "indoors, so anything under ~100m will report crossings you didn't make. " +
                        "You can also drag the pin or circle edge on the map.",
                    fontFamily = TerminalFontFamily,
                    color = NervDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                com.evyr.rebuild.ui.components.GeofenceMapPicker(
                    initialLat = place.latitude,
                    initialLng = place.longitude,
                    radiusMeters = pendingRadius,
                    onChange = { lat, lng, radius ->
                        pendingLat = lat
                        pendingLng = lng
                        pendingRadius = radius
                        radiusText = radius.toInt().toString()
                    },
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = { adjustingOnMapFor = null }) {
                        Text("CANCEL", fontFamily = TerminalFontFamily, color = NervDim)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val updated = place.copy(
                                    latitude = pendingLat,
                                    longitude = pendingLng,
                                    radiusMeters = pendingRadius
                                )
                                viewModel.addGeofencePlace(updated)
                                geofenceManager.removeGeofence(place)
                                geofenceManager.registerGeofence(updated) { success, error ->
                                    reregisterStatus = reregisterStatus + (place.id to
                                        if (success) "Active" else "Failed${error?.let { ": $it" } ?: ""}")
                                }
                            }
                            adjustingOnMapFor = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
                    ) {
                        Text("SAVE", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: GeofencePlace,
    onDelete: () -> Unit,
    onToggleArrival: (Boolean) -> Unit,
    onToggleDeparture: (Boolean) -> Unit,
    onEditChecklist: () -> Unit,
    onReregister: () -> Unit,
    onAdjustOnMap: () -> Unit,
    reregisterStatus: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(place.label.uppercase(), fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 13.sp)
                Text(
                    "%.4f, %.4f · R=%.0fm".format(place.latitude, place.longitude, place.radiusMeters),
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete place", tint = NervRed, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EXIT NUDGE", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = place.notifyOnDeparture,
                onCheckedChange = onToggleDeparture,
                colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ENTER NUDGE", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = place.notifyOnArrival,
                onCheckedChange = onToggleArrival,
                colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "EDIT CHECKLIST FOR THIS PLACE",
            fontFamily = TerminalFontFamily,
            color = NervOrange,
            fontSize = 11.sp,
            modifier = Modifier
                .clickable { onEditChecklist() }
                .padding(vertical = 4.dp)
        )
        // Places saved before the id=0 registration bug was fixed may have
        // registered under the wrong request id and never actually trigger.
        // Lets a place be fixed in place instead of requiring delete + recreate.
        Text(
            "RE-REGISTER GEOFENCE",
            fontFamily = TerminalFontFamily,
            color = NervDim,
            fontSize = 11.sp,
            modifier = Modifier
                .clickable { onReregister() }
                .padding(vertical = 4.dp)
        )
        Text(
            "ADJUST ON MAP",
            fontFamily = TerminalFontFamily,
            color = NervOrange,
            fontSize = 11.sp,
            modifier = Modifier
                .clickable { onAdjustOnMap() }
                .padding(vertical = 4.dp)
        )
        reregisterStatus?.let {
            Text(it, fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 11.sp)
        }
    }
}

/**
 * Extra directives checked specifically at this place, on top of whatever's
 * NFC-linked (which still applies everywhere, unchanged). Daily and Weekly only --
 * Standing/streak items and display-only Mindset lines have no per-day completion
 * for a location check to test against.
 */
@Composable
private fun PlaceChecklistDialog(
    place: GeofencePlace,
    viewModel: MainViewModel,
    daily: List<Directive>,
    weekly: List<Directive>,
    onDismiss: () -> Unit
) {
    val linksFlow = remember(place.id) { viewModel.observePlaceLinks(place.id) }
    val links by linksFlow.collectAsState()
    val byId = links.associateBy { it.directiveId }
    val all = daily + weekly

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NervPanel,
        title = { Text("${place.label.uppercase()} CHECKLIST", fontFamily = TerminalFontFamily, color = NervOrange) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "OUT items go on the checklist when you leave this place, IN items when you arrive. " +
                        "NFC items are set separately with their own OUT / IN in NFC Tags.",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                if (all.isEmpty()) {
                    Text("No Daily or Weekly directives yet.", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp)
                }
                all.forEach { d ->
                    val link = byId[d.id]
                    val out = link?.onExit == true
                    val inn = link?.onEnter == true
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            d.title, fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        PlaceListToggle("OUT", out) { viewModel.setPlaceLink(place.id, d.id, !out, inn) }
                        Spacer(Modifier.width(6.dp))
                        PlaceListToggle("IN", inn) { viewModel.setPlaceLink(place.id, d.id, out, !inn) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DONE", fontFamily = TerminalFontFamily, color = NervGreen)
            }
        }
    )
}

@Composable
private fun PlaceListToggle(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = TerminalFontFamily,
        color = if (on) androidx.compose.ui.graphics.Color.Black else NervDim,
        fontSize = 11.sp,
        modifier = Modifier
            .background(
                if (on) NervOrange else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
