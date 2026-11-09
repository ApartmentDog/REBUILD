package com.evyr.rebuild.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

/**
 * HOARD. Sealed until five 🔥 hard objectives are cleared.
 *
 * Every completion ever logged is a coin on the pile; every cleared 🔥
 * objective is a gem set into it, listed below as a relic. The drake on top
 * reads today's Sync Ratio: asleep under 30, watching otherwise, and
 * breathing fire once the ratio is pushed past 100.
 */
@Composable
fun HoardScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val hard by viewModel.hardItems.collectAsState()
    val coins by viewModel.lifetimeCompletions.collectAsState()
    val sync by viewModel.syncRatio.collectAsState()
    val relics = remember(hard) {
        hard.filter { it.hardClearedEpochMs != null }.sortedBy { it.hardClearedEpochMs }
    }
    val mood = when {
        sync > 100 -> DrakeMood.BERSERK
        sync < 30 -> DrakeMood.DORMANT
        else -> DrakeMood.WATCHFUL
    }

    Column(modifier = Modifier.fillMaxSize().background(EvaBackground)) {
        HazardStripe(height = 6.dp, colorA = Ember, colorB = EvaOutline)
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EvaTextPrimary)
            }
            Text("THE HOARD", fontFamily = TerminalFontFamily, color = Gold, fontSize = 16.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                mood.label,
                fontFamily = TerminalFontFamily,
                color = mood.color,
                fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold
            )
        }
        HazardStripe(height = 6.dp, colorA = Ember, colorB = EvaOutline)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HoardScene(
                    coins = coins,
                    gems = relics.size,
                    mood = mood,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .border(3.dp, EvaOutline)
                        .background(Cave)
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("COINS", coins.toString(), Gold)
                    Stat("RELICS", relics.size.toString(), Ember)
                    Stat("SYNC", "$sync%", mood.color)
                }
            }
            item {
                Text(
                    "Every coin, a thing done. Every gem, a fire walked through.",
                    fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 10.sp
                )
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("RELICS", fontFamily = TerminalFontFamily, color = Gold, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            }
            items(relics, key = { it.id }) { r ->
                RelicRow(r, index = relics.indexOf(r))
            }
        }
    }
}

private enum class DrakeMood(val label: String, val color: Color) {
    DORMANT("DORMANT", Color(0xFF7A7A80)),
    WATCHFUL("WATCHFUL", Color(0xFFFFCC00)),
    BERSERK("BERSERK", Color(0xFFFF6A00))
}

private val Gold = Color(0xFFFFCC00)
private val GoldDark = Color(0xFFB8860B)
private val GoldLight = Color(0xFFFFE680)
private val Ember = Color(0xFFFF6A00)
private val Cave = Color(0xFF141012)

private val GemColors = listOf(
    Color(0xFFE0173D), // ruby
    Color(0xFF3DD6C8), // aquamarine
    Color(0xFF3D8AFF), // sapphire
    Color(0xFFB53DFF), // amethyst
    Color(0xFF4ADE80), // emerald
    Color(0xFFFF9F1C)  // topaz
)

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = TerminalFontFamily, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 9.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun RelicRow(relic: Directive, index: Int) {
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val gem = GemColors[index % GemColors.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EvaPanelBg)
            .border(1.dp, gem.copy(alpha = 0.5f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(18.dp)) { drawGem(center, size.minDimension / 2f, gem, 1f) }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(relic.title.uppercase(), fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "CLEARED ${relic.hardClearedEpochMs?.let { fmt.format(Date(it)).uppercase() } ?: "?"} · +${relic.hardBoost}%",
                fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 9.sp
            )
        }
        Text("#${index + 1}", fontFamily = TerminalFontFamily, color = gem, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- The scene ----------

// Wingless drake, lying on the pile, facing right. One char per pixel.
// O body, D shade/outline, B belly, E eye, K pupil, H horn, h far horn,
// S spine ridge, C claw, N nostril.
private val DRAKE = listOf(
    "..............................HH................",
    "...............................HHH..............",
    "................................hhHHHDDDDD......",
    ".................................DDDOOOOOOD.....",
    "................................DOOOOOOOOOOD....",
    "................................DOOOOEKOOOOODD..",
    "...............................DOOOOOEEOOOOOOOD.",
    "...............................DOOOOOOOOOOOOONOD",
    "..........S...S...S............DOOOOODDDDDDDDDD.",
    ".......SSSDSSSDSSSDSSS.........DOOOOOBBBBBBBD...",
    ".....SDDOOOOOOOOOOOOOODDS.....DOOOOBBD..........",
    "....DOOOOOOOOOOOOOOOOOOOODD..DOOOOBBD...........",
    "...DOOOOOOOOOOOOOOOOOOOOOOODDOOOOBBD............",
    "..DOOOOOOOOOOOOOOOOOOOOOOOOOOOOOBBD.............",
    "..DOOOODDOOOOOOOOOOOOOOOOOOOOOOBBD..............",
    ".DOOODOODOOOOOOOOOOOOOOOOOODOOBBD...............",
    ".DOOD.DOOBBBBBBBBBBBBBBBBBBDOOOBDDDD............",
    "DOOD...DOOOODDDDDDDDDDDDDDDOOOOOOOCCC...........",
    "DOOOD...DOOOOOCCC......DDDDDDDDDDDD.............",
    ".DOOOOOOOODDDDD.................................",
    "..DDDDDDDD......................................"
)
private const val DRAKE_W = 48
private const val DRAKE_H = 21
private const val SNOUT_ROW = 7

private val DrakePalette = mapOf(
    'O' to Color(0xFFE8731A),
    'D' to Color(0xFF963E0A),
    'B' to Color(0xFFFADEAA),
    'E' to Color(0xFFFFF078),
    'K' to Color(0xFF141414),
    'H' to Color(0xFFF0E6D2),
    'h' to Color(0xFFB4AA96),
    'S' to Color(0xFF782D05),
    'C' to Color(0xFFF0E6D2),
    'N' to Color(0xFF5A1E05)
)

@Composable
private fun HoardScene(coins: Int, gems: Int, mood: DrakeMood, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "hoard")
    val clock by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "clock"
    )
    val breath by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath"
    )

    // Coin positions are fixed per count, so the pile doesn't reshuffle every frame.
    val coinCount = coins.coerceIn(0, 260)
    val coinSeeds = remember(coinCount) {
        val rnd = Random(0x0D2A6E)
        List(coinCount) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val floorY = h * 0.94f

        // Pile height grows with the log of coins: a few coins is a scatter,
        // hundreds is a mound, and it never swallows the drake.
        val pileH = (h * 0.08f + h * 0.05f * ln(1f + coins.toFloat())).coerceAtMost(h * 0.40f)
        val pileTop = floorY - pileH
        val pileCx = w * 0.42f
        val pileHalfW = w * 0.46f

        fun pileSurfaceY(x: Float): Float {
            val d = ((x - pileCx) / pileHalfW).coerceIn(-1f, 1f)
            return floorY - pileH * (1f - d * d)
        }

        // Pile body
        val pile = Path().apply {
            moveTo(pileCx - pileHalfW, floorY)
            var x = pileCx - pileHalfW
            while (x <= pileCx + pileHalfW) { lineTo(x, pileSurfaceY(x)); x += 4f }
            lineTo(pileCx + pileHalfW, floorY)
            close()
        }
        drawPath(pile, GoldDark)
        drawRect(Color(0xFF2A1F14), Offset(0f, floorY), Size(w, h - floorY))

        // Coins scattered through the pile, brighter toward the surface.
        val coinR = (w * 0.012f).coerceAtLeast(3f)
        coinSeeds.forEach { (a, b, c) ->
            val x = pileCx - pileHalfW + a * pileHalfW * 2f
            val top = pileSurfaceY(x)
            val y = top + b * b * (floorY - top)
            val near = 1f - b
            drawCircle(if (c > 0.5f) Gold else GoldLight.copy(alpha = 0.6f + 0.4f * near), coinR, Offset(x, y))
            drawCircle(GoldDark, coinR * 0.45f, Offset(x, y))
        }

        // Gems set into the surface, one per relic, twinkling out of step.
        val gemRnd = Random(0x6E3)
        repeat(gems.coerceAtMost(40)) { i ->
            val x = pileCx - pileHalfW * 0.85f + gemRnd.nextFloat() * pileHalfW * 1.7f
            val y = pileSurfaceY(x) + gemRnd.nextFloat() * pileH * 0.35f
            val tw = 0.6f + 0.4f * sin((clock + i * 0.17f) * 2f * Math.PI.toFloat())
            drawGem(Offset(x, y), (w * 0.022f).coerceAtLeast(6f), GemColors[i % GemColors.size], tw)
        }

        // Drake, resting on the pile.
        val px = (w * 0.62f) / DRAKE_W
        val drakeLeft = w * 0.06f
        val drakeBottom = pileTop + pileH * 0.30f
        val drakeTop = drakeBottom - DRAKE_H * px - breath * px * 0.5f
        val blinking = mood == DrakeMood.WATCHFUL && clock in 0.48f..0.52f
        val eyesShut = mood == DrakeMood.DORMANT || blinking

        DRAKE.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                if (ch == '.') return@forEachIndexed
                val color = when {
                    (ch == 'E' || ch == 'K') && eyesShut -> if (row == 5) DrakePalette.getValue('D') else DrakePalette.getValue('O')
                    ch == 'E' && mood == DrakeMood.BERSERK -> Ember
                    else -> DrakePalette.getValue(ch)
                }
                drawRect(color, Offset(drakeLeft + col * px, drakeTop + row * px), Size(px + 0.5f, px + 0.5f))
            }
        }

        val snout = Offset(drakeLeft + DRAKE_W * px, drakeTop + (SNOUT_ROW + 0.5f) * px)
        when (mood) {
            DrakeMood.BERSERK -> drawFlame(snout, w - snout.x, px, clock)
            DrakeMood.DORMANT -> drawSleep(Offset(snout.x - 6 * px, drakeTop - px), px, clock)
            DrakeMood.WATCHFUL -> {
                // A thin wisp of smoke from the nostril.
                val k = clock
                drawCircle(Color.White.copy(alpha = 0.25f * (1f - k)), px * (0.6f + k), Offset(snout.x + k * px * 4, snout.y - k * px * 6))
            }
        }
    }
}

private fun DrawScope.drawGem(c: Offset, r: Float, color: Color, twinkle: Float) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        lineTo(c.x + r * 0.8f, c.y - r * 0.2f)
        lineTo(c.x, c.y + r)
        lineTo(c.x - r * 0.8f, c.y - r * 0.2f)
        close()
    }
    drawPath(p, color.copy(alpha = 0.55f + 0.45f * twinkle))
    drawCircle(Color.White.copy(alpha = 0.7f * twinkle), r * 0.22f, Offset(c.x - r * 0.25f, c.y - r * 0.35f))
}

/** Pixel-flame cone from the snout, flickering. */
private fun DrawScope.drawFlame(origin: Offset, room: Float, px: Float, clock: Float) {
    val len = (room * 0.92f).coerceAtLeast(px * 4)
    val steps = (len / px).toInt().coerceAtLeast(1)
    val flameColors = listOf(Color(0xFFFFF3B0), Color(0xFFFFCC00), Color(0xFFFF6A00), Color(0xFFC8102E))
    for (i in 0 until steps) {
        val f = i.toFloat() / steps
        val spread = (1 + f * 5).toInt()
        for (j in -spread..spread) {
            val flick = sin((clock * 12f + i * 0.9f + j * 1.7f) * Math.PI.toFloat())
            if (flick < -0.35f + f * 0.6f) continue
            val color = flameColors[((f * 3f) + if (j == 0) 0f else 0.6f).toInt().coerceIn(0, 3)]
            drawRect(
                color.copy(alpha = (1f - f * 0.7f)),
                Offset(origin.x + i * px, origin.y + j * px * 0.8f - px / 2f),
                Size(px, px)
            )
        }
    }
}

/** Drifting Z's above a sleeping head. */
private fun DrawScope.drawSleep(at: Offset, px: Float, clock: Float) {
    repeat(3) { i ->
        val k = (clock + i / 3f) % 1f
        val s = px * (1.2f + i * 0.4f)
        val x = at.x + k * px * 6 + i * px * 2
        val y = at.y - k * px * 10
        val a = (1f - k)
        val c = Color.White.copy(alpha = 0.7f * a)
        drawLine(c, Offset(x, y), Offset(x + s, y), px * 0.4f)
        drawLine(c, Offset(x + s, y), Offset(x, y + s), px * 0.4f)
        drawLine(c, Offset(x, y + s), Offset(x + s, y + s), px * 0.4f)
    }
}
