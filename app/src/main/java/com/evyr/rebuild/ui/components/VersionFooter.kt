package com.evyr.rebuild.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.BuildConfig
import com.evyr.rebuild.ui.theme.NervDim
import com.evyr.rebuild.ui.theme.TerminalFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows versionName + short git commit hash, so "what build am I running"
 * is always answerable without guessing -- the hash is injected at build
 * time (app/build.gradle.kts, GIT_COMMIT_HASH) from whatever commit CI
 * actually built, so it can't go stale the way a manually-bumped version
 * number would.
 *
 * compact = true: just "v1.0 · a1b2c3d" (for Dashboard footer)
 * compact = false: adds the build date too (for Settings, more useful when
 * reporting a bug -- "which build has the NFC fix" needs the date, not just hash)
 *
 * onSecretTap, if provided, fires after 4 taps within 2 seconds of each other --
 * a stray tap weeks apart doesn't count, has to be a deliberate quick sequence.
 * This component stays dumb about what happens after the taps (no dialog, no
 * navigation here) -- the caller decides what "unlocked" means.
 */
@Composable
fun VersionFooter(modifier: Modifier = Modifier, compact: Boolean = false, onSecretTap: (() -> Unit)? = null) {
    val text = if (compact) {
        "v${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_COMMIT_HASH}"
    } else {
        val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
        "v${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_COMMIT_HASH} · built ${fmt.format(Date(BuildConfig.BUILD_TIMESTAMP))}"
    }

    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val tapWindowMs = 2000L

    Row(
        modifier = modifier.fillMaxWidth().let {
            if (onSecretTap != null) {
                it.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val now = System.currentTimeMillis()
                    tapCount = if (now - lastTapTime <= tapWindowMs) tapCount + 1 else 1
                    lastTapTime = now
                    if (tapCount >= 4) {
                        tapCount = 0
                        onSecretTap()
                    }
                }
            } else it
        },
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            fontFamily = TerminalFontFamily,
            color = NervDim,
            fontSize = 9.sp
        )
    }
}
