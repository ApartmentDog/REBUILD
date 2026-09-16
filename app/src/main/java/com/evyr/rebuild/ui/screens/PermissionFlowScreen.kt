package com.evyr.rebuild.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.theme.*
import com.evyr.rebuild.ui.components.HazardStripe

/**
 * Explains and requests permissions one at a time, in this order:
 *   1. Notifications (POST_NOTIFICATIONS, Android 13+)
 *   2. Foreground location (ACCESS_FINE_LOCATION) — needed before background can be requested
 *   3. Background location (ACCESS_BACKGROUND_LOCATION) — separate step per Android policy
 *   4. NFC — no runtime prompt needed, just a capability check
 *
 * Each step shows plain-language "why" before the system dialog fires, per the
 * design doc note: batch asks with clear explanations rather than demanding
 * everything on first launch.
 */
private fun allGranted(context: android.content.Context): Boolean {
    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms += Manifest.permission.ACCESS_BACKGROUND_LOCATION
    return perms.all {
        androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun PermissionFlowScreen(onComplete: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var step by remember { mutableStateOf(if (allGranted(context)) 99 else 0) }
    if (step == 99) { LaunchedEffect(Unit) { onComplete() }; return }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { step = 2 }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { step = 3 }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onComplete() }

    Column(
        modifier = Modifier.fillMaxSize().background(NervBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        when (step) {
            0 -> PermissionStep(
                title = "NOTIFICATIONS",
                explanation = "Needed to send you the wallet/keys nudges and daily summaries.",
                onContinue = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else step = 2
                },
                onSkip = { step = 2 }
            )
            2 -> PermissionStep(
                title = "LOCATION (FOREGROUND)",
                explanation = "Needed so the app knows when you arrive at or leave home, for the wallet/keys check.",
                onContinue = { fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                onSkip = { onComplete() }
            )
            3 -> PermissionStep(
                title = "LOCATION (BACKGROUND)",
                explanation = "Needed so the reminder still fires when the app isn't open — this is what makes the geofence actually useful.",
                onContinue = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else onComplete()
                },
                onSkip = { onComplete() }
            )
        }
    }
    }
}

@Composable
private fun PermissionStep(
    title: String,
    explanation: String,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Text(title, fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 16.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(12.dp))
    Text(explanation, fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 13.sp)
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onContinue,
        colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
    ) {
        Text("GRANT", fontFamily = TerminalFontFamily)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onSkip) {
        Text("SKIP FOR NOW", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp)
    }
}
