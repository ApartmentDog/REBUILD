package com.evyr.rebuild.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.security.PinManager
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Full-screen PIN lock. Renders on every app open per spec — nothing behind it
 * renders until the correct PIN is entered. First launch flows into "set PIN" instead.
 */
@Composable
fun PinLockScreen(
    pinManager: PinManager,
    onUnlocked: () -> Unit
) {
    var hasPin by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { hasPin = pinManager.hasPinSet() }

    when (hasPin) {
        null -> Box(Modifier.fillMaxSize().background(NervBackground))
        false -> SetPinFlow(pinManager) { hasPin = true }
        true -> EnterPinFlow(pinManager, onUnlocked)
    }
}

@Composable
private fun SetPinFlow(pinManager: PinManager, onSet: () -> Unit) {
    var stage by remember { mutableStateOf(0) } // 0 = enter, 1 = confirm
    var firstEntry by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    PinScaffold(
        title = if (stage == 0) "SET PASSCODE" else "CONFIRM PASSCODE",
        pin = current,
        error = error,
        onDigit = { d ->
            if (current.length < 6) current += d
        },
        onBackspace = { if (current.isNotEmpty()) current = current.dropLast(1) },
        onSubmit = {
            if (current.length < 4) {
                error = "MIN 4 DIGITS"
                return@PinScaffold
            }
            if (stage == 0) {
                firstEntry = current
                current = ""
                stage = 1
                error = null
            } else {
                if (current == firstEntry) {
                    scope.launch {
                        pinManager.setPin(current)
                        onSet()
                    }
                } else {
                    error = "MISMATCH — TRY AGAIN"
                    current = ""
                    stage = 0
                    firstEntry = ""
                }
            }
        }
    )
}

@Composable
private fun EnterPinFlow(pinManager: PinManager, onUnlocked: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }

    val context = androidx.compose.ui.platform.LocalContext.current
    // Resolved to a single non-null value up front rather than null-checking at
    // each use site: smart casts through nested lambdas are fragile, and this
    // reads clearer anyway. Null means "no biometric path available".
    val biometricActivity = remember(context) {
        (context as? androidx.fragment.app.FragmentActivity)
            ?.takeIf { com.evyr.rebuild.security.BiometricUnlock.isAvailable(context) }
    }

    // Offer the fingerprint prompt immediately on arriving at the lock screen, so
    // the common case is a single touch. Cancelling just leaves the keypad ready;
    // the PIN is always still available.
    LaunchedEffect(biometricActivity) {
        biometricActivity?.let { act ->
            com.evyr.rebuild.security.BiometricUnlock.prompt(
                activity = act,
                onSuccess = onUnlocked,
                onFailure = { error = it }
            )
        }
    }

    PinScaffold(
        title = "ENTER PASSCODE",
        pin = current,
        error = error,
        onBiometric = biometricActivity?.let { act ->
            {
                com.evyr.rebuild.security.BiometricUnlock.prompt(
                    activity = act,
                    onSuccess = onUnlocked,
                    onFailure = { error = it }
                )
            }
        },
        onDigit = { d -> if (current.length < 6) current += d },
        onBackspace = { if (current.isNotEmpty()) current = current.dropLast(1) },
        onSubmit = {
            scope.launch {
                if (pinManager.verifyPin(current)) {
                    onUnlocked()
                } else {
                    error = "INCORRECT"
                    current = ""
                }
            }
        }
    )
}

@Composable
private fun PinScaffold(
    title: String,
    pin: String,
    error: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    // Null when the device has no enrolled biometric -- the button is then hidden
    // entirely rather than shown in a state that can't work.
    onBiometric: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NervBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HazardStripe()
        Text("ACCESS RESTRICTED", fontFamily = TerminalFontFamily, color = NervRed, fontSize = 22.sp, letterSpacing = 3.sp)
        Text("EVYR ONLY", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 14.sp, letterSpacing = 4.sp)
        HazardStripe()
        Spacer(Modifier.height(12.dp))
        Text(title, fontFamily = TerminalFontFamily, color = NervDim, fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { i ->
                val filled = i < pin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) NervGreen else NervPanelBorder)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = error ?: " ",
            fontFamily = TerminalFontFamily,
            color = NervRed,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(24.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (key.isNotEmpty()) NervPanel else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key.isNotEmpty()) {
                            TextButtonKey(key) {
                                when (key) {
                                    "⌫" -> onBackspace()
                                    else -> onDigit(key)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSubmit,
            colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
        ) {
            Text("CONFIRM", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold)
        }

        onBiometric?.let { trigger ->
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = trigger) {
                Text(
                    "USE FINGERPRINT",
                    fontFamily = TerminalFontFamily,
                    color = NervGreen,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun TextButtonKey(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            fontFamily = TerminalFontFamily,
            color = NervGreen,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
