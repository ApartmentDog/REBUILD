package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.location.GeofenceDebugLog
import com.evyr.rebuild.ui.theme.*

/**
 * Viewer for GeofenceDebugLog, so "geofencing isn't working" can actually be
 * diagnosed from the phone itself -- no cable, no adb. Built specifically
 * because every layer of the geofence -> worker -> notification chain had
 * silent-failure branches that looked identical to "never ran at all" from
 * the outside. This shows which branch actually fired.
 */
@Composable
fun GeofenceDebugSection() {
    val context = LocalContext.current
    var events by remember { mutableStateOf(GeofenceDebugLog.readAll(context)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(
            "Last 50 geofence events, newest first. Cross a Home/Work boundary, then come back here and REFRESH.",
            fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "REFRESH",
                fontFamily = TerminalFontFamily,
                color = NervOrange,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { events = GeofenceDebugLog.readAll(context) }
                    .padding(vertical = 4.dp)
            )
            Text(
                "CLEAR",
                fontFamily = TerminalFontFamily,
                color = NervDim,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable { GeofenceDebugLog.clear(context); events = emptyList() }
                    .padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        if (events.isEmpty()) {
            Text(
                "No events recorded yet. If this stays empty after crossing a boundary, the OS never called REBUILD at all -- that points at location permission, battery optimization, or Google Play services rather than anything in the app itself.",
                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                events.forEach { line ->
                    Text(
                        line,
                        fontFamily = TerminalFontFamily,
                        color = NervGreen,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
