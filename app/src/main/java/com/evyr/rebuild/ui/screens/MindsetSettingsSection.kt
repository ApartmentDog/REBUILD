package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.theme.*

/**
 * Extracted from SettingsScreen.kt during the cleanup pass. No behavior change,
 * purely moved. Calls DirectiveEditDialog, which stays reachable via same-package
 * visibility (both remain in com.evyr.rebuild.ui.screens).
 */
@Composable
fun MindsetSection(viewModel: MainViewModel) {
    val mindset by viewModel.mindsetItems.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingDirective by remember { mutableStateOf<Directive?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(
            "One of these is shown at random on your first unlock each day.",
            fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))

        if (mindset.isEmpty()) {
            Text(
                "No mindset lines yet — the daily quote is skipped until you add one.",
                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
            )
        }

        mindset.forEach { d ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingDirective = d },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = d.title,
                    fontFamily = TerminalFontFamily,
                    color = NervGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                )
                IconButton(onClick = { viewModel.deleteDirective(d) }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = NervRed, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "+ ADD LINE",
            fontFamily = TerminalFontFamily,
            color = NervGreen,
            fontSize = 11.sp,
            modifier = Modifier
                .clickable { showAddDialog = true }
                .padding(vertical = 4.dp)
        )
    }

    if (showAddDialog) {
        AddMindsetLineDialog(
            onDismiss = { showAddDialog = false },
            onSave = { viewModel.addDirective(it); showAddDialog = false }
        )
    }

    editingDirective?.let { d ->
        DirectiveEditDialog(
            existing = d,
            onDismiss = { editingDirective = null },
            onSave = { viewModel.addDirective(it); editingDirective = null }
        )
    }
}

