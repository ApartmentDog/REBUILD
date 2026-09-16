package com.evyr.rebuild.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.evyr.rebuild.R

// ---- EVA-02 Hazard Palette (replaces the old NERV terminal green/orange scheme) ----
// Reference: EVA-02 plugsuit red, hazard-stripe yellow/black, thick cel-shading
// outlines. Chosen after several rounds finding the muted terminal-green look
// too flat/quiet and several "soft glow" variants too generic -- this is the
// one that actually landed.
val EvaBackground = Color(0xFF0C0C0E)
val EvaRed = Color(0xFFC8102E)
val EvaRedLight = Color(0xFFE0173D)
val EvaRedDark = Color(0xFF96001F)
val EvaYellow = Color(0xFFFFCC00)
val EvaYellowLight = Color(0xFFFFD93D)
val EvaYellowDark = Color(0xFFE0A800)
val EvaPanelBg = Color(0xFF1C1C1F)
val EvaPanelBgLight = Color(0xFF28282C)
val EvaPanelShadow = Color(0xFF131315)
val EvaTextPrimary = Color(0xFFE8E8EA)
val EvaTextDim = Color(0xFF7A7A80)
val EvaGreen = Color(0xFF4ADE80) // status-nominal accent only, not a general UI color
val EvaOutline = Color(0xFF000000)

// Beige 90s-hardware console housing that frames the whole app
// Base tone set to #C0B89B by request; the light/dark variants and the label are
// shifted to match so the housing still reads as molded plastic with a highlight
// and shadow, rather than a flat slab.
val ConsoleBeige = Color(0xFFC0B89B)
val ConsoleBeigeLight = Color(0xFFD2CAAD)
val ConsoleBeigeDark = Color(0xFFA69E82)
val ConsoleBezel = Color(0xFF26241D)
val ConsoleVent = Color(0xFF938A6E)
val ConsoleLabel = Color(0xFF6B6350)

// ---- Legacy aliases ----
// Old screens reference these names throughout; rather than rename every call
// site in one risky pass, point the old names at the new palette so existing
// code keeps compiling and inherits the new look. New code should prefer the
// Eva* names above directly.
val NervBackground = EvaBackground
val NervPanel = EvaPanelBg
val NervOrange = EvaYellow
val NervGreen = EvaTextPrimary
val NervRed = EvaRed
val NervDim = EvaTextDim
val NervPanelBorder = EvaOutline
val NervMagenta = EvaRedLight
val NervCyan = EvaYellowLight
val NervHexLine = EvaRed

/** Real terminal font, loaded from res/font/share_tech_mono.ttf. */
val TerminalFontFamily = FontFamily(
    Font(R.font.share_tech_mono)
)

private val NervColorScheme = darkColorScheme(
    background = EvaBackground,
    surface = EvaPanelBg,
    primary = EvaYellow,
    secondary = EvaRed,
    error = EvaRed,
    onBackground = EvaTextPrimary,
    onSurface = EvaTextPrimary,
    onPrimary = Color.Black,
    onSecondary = Color.White
)

@Composable
fun RebuildTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NervColorScheme,
        typography = NervTypography,
        content = content
    )
}

/**
 * Shared OutlinedTextField color scheme, matching REBUILD's theme. Was copy-pasted
 * identically 10 times across 5 screen files (SettingsScreen, MacroTrackerScreen,
 * NutritionGoalsScreen, GeofencePlacesScreen, NfcRegistrationScreen) -- one
 * definition here means a future palette change is one edit instead of ten,
 * and removes the risk of one of those ten spots quietly drifting out of sync.
 */
@Composable
fun rebuildTextFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = NervGreen,
    unfocusedTextColor = NervGreen,
    focusedBorderColor = NervOrange,
    unfocusedBorderColor = NervDim
)
