package com.evyr.rebuild.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.data.ItemKind
import com.evyr.rebuild.data.RecurrenceType
import com.evyr.rebuild.data.Zone
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.components.HexGridBackground
import com.evyr.rebuild.ui.components.VersionFooter
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenNfcTags: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenGuidedSessions: () -> Unit,
    onResetDailyQuote: () -> Unit
) {
    val daily by viewModel.dailyItems.collectAsState()
    val weekly by viewModel.weeklyItems.collectAsState()
    val standing by viewModel.standingItems.collectAsState()
    val mindset by viewModel.mindsetItems.collectAsState()
    val allDirectives = daily + weekly + standing + mindset
    val prefs by viewModel.notificationPrefs.collectAsState()
    val context = LocalContext.current
    var currentTheme by remember { mutableStateOf(com.evyr.rebuild.IconSwitcher.currentTheme(context)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingDirective by remember { mutableStateOf<Directive?>(null) }
    var managingRemindersFor by remember { mutableStateOf<Directive?>(null) }

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
            Text("SETTINGS", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 16.sp, letterSpacing = 2.sp)
        }
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onOpenNfcTags) {
                        Text("NFC TAGS", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
                    }
                    TextButton(onClick = onOpenLocations) {
                        Text("LOCATIONS", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onOpenGuidedSessions) {
                        Text("GUIDED SESSIONS", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "NOTIFICATIONS",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                NotificationPrefsPanel(prefs = prefs, onUpdate = viewModel::updateNotificationPrefs)
                Spacer(Modifier.height(6.dp))
                BatteryOptimizationField()

                Spacer(Modifier.height(16.dp))
                Text(
                    "APP IDENTITY",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Switches the home-screen icon and name. The launcher may take a moment (or a home-screen refresh) to show the change.",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        com.evyr.rebuild.LauncherTheme.NERV to "NERV",
                        com.evyr.rebuild.LauncherTheme.REDEMPTION to "REDEMPTION"
                    ).forEach { (theme, label) ->
                        val selected = currentTheme == theme
                        Text(
                            label,
                            fontFamily = TerminalFontFamily,
                            color = if (selected) NervBackground else NervGreen,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .background(if (selected) NervOrange else NervPanelBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    com.evyr.rebuild.IconSwitcher.setTheme(context, theme)
                                    currentTheme = theme
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "SYNC RATIO DEBUG",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                SyncRatioDebugPanel(viewModel = viewModel)

                Spacer(Modifier.height(16.dp))
                Text(
                    "GEOFENCE DEBUG",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                GeofenceDebugSection()

                Spacer(Modifier.height(16.dp))
                Text(
                    "BACKUP",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                BackupField(viewModel = viewModel)

                Spacer(Modifier.height(16.dp))
                Text(
                    "MINDSET",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "SHOW DAILY QUOTE AGAIN",
                    fontFamily = TerminalFontFamily,
                    color = NervOrange,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clickable { onResetDailyQuote() }
                        .padding(vertical = 4.dp)
                )
                Text(
                    "Clears today's marker — it'll show again next time you unlock, useful for testing.",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
                )
                Spacer(Modifier.height(4.dp))
                MindsetSection(viewModel = viewModel)

                Spacer(Modifier.height(16.dp))
                Text(
                    "DIRECTIVES",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
            }
            items(allDirectives) { d ->
                DirectiveEditRow(
                    directive = d,
                    onEdit = { editingDirective = d },
                    onReminders = { managingRemindersFor = d },
                    onDelete = { viewModel.deleteDirective(d) }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
        ) {
            Text("+ ADD DIRECTIVE", fontFamily = TerminalFontFamily)
        }

        VersionFooter(modifier = Modifier.padding(bottom = 12.dp))
    }
    }

    if (showAddDialog) {
        DirectiveEditDialog(
            existing = null,
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

    managingRemindersFor?.let { d ->
        ReminderManagementDialog(
            directive = d,
            viewModel = viewModel,
            onDismiss = { managingRemindersFor = null }
        )
    }
}

