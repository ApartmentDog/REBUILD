package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.theme.*

/**
 * Extracted from SettingsScreen.kt during the cleanup pass. No behavior change,
 * purely moved.
 */
@Composable
fun BatteryOptimizationField() {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Re-checked each recomposition after returning from the system dialog rather
    // than cached in remember{}, so the status updates immediately once granted
    // instead of showing stale "not exempt" after the user just fixed it.
    val powerManager = context.getSystemService(android.os.PowerManager::class.java)
    val isExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text("Background reliability", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            if (isExempt)
                "Exempt from battery optimization — geofence nudges and the weekly backup can run reliably."
            else
                "Not exempt. Many phones (Samsung, Xiaomi, OnePlus especially) kill scheduled background " +
                    "work by default, which silently stops geofence nudges and the weekly backup without any error.",
            fontFamily = TerminalFontFamily,
            color = if (isExempt) NervDim else NervRed,
            fontSize = 9.sp
        )
        if (!isExempt) {
            Spacer(Modifier.height(6.dp))
            Text(
                "REQUEST EXEMPTION",
                fontFamily = TerminalFontFamily,
                color = NervOrange,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

