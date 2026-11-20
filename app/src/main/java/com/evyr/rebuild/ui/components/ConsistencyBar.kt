package com.evyr.rebuild.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evyr.rebuild.data.AppRepository.DayMark
import com.evyr.rebuild.ui.theme.EvaRed
import com.evyr.rebuild.ui.theme.EvaTextDim
import com.evyr.rebuild.ui.theme.EvaYellow

/**
 * One square per day, oldest on the left, today on the right. Yellow = done,
 * half yellow = minimum version, red = missed or slipped, outline = still
 * open, dark = not due or not tracked yet.
 */
@Composable
fun ConsistencyBar(cells: List<DayMark>, modifier: Modifier = Modifier, cell: Dp = 7.dp) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.forEach { mark ->
            val (fill, edge) = when (mark) {
                DayMark.DONE -> EvaYellow to EvaYellow
                DayMark.MIN -> EvaYellow.copy(alpha = 0.4f) to EvaYellow
                DayMark.MISSED -> EvaRed to EvaRed
                DayMark.OPEN -> Color.Transparent to EvaTextDim
                DayMark.OFF -> OffCell to OffCell
            }
            Box(Modifier.size(cell).background(fill).border(1.dp, edge))
        }
    }
}

private val OffCell = Color(0xFF2A2A2E)
