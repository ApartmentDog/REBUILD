package com.evyr.rebuild.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Extracted from SettingsScreen.kt during the cleanup pass -- this file was one
 * of seven sections crammed into a single ~900 line file. No behavior change,
 * purely moved.
 */
@Composable
fun BackupField(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val folders = remember { com.evyr.rebuild.data.BackupFolderManager(context) }

    var folderLabel by remember { mutableStateOf(folders.folderLabel()) }
    var lastStatus by remember { mutableStateOf(folders.lastBackupStatus()) }
    var lastAt by remember { mutableStateOf(folders.lastBackupAtEpochMs()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val fmt = remember { java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.US) }

    val folderPicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            folders.setFolder(uri)
            folderLabel = folders.folderLabel()
            message = "Backup folder set"
            isError = false
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingRestoreUri = uri }

    fun runBackupNow() {
        busy = true
        message = null
        scope.launch {
            val target = folders.createBackupFile()
            if (target == null) {
                message = "Pick a backup folder first"
                isError = true
            } else {
                when (val r = viewModel.writeArchive(context, target)) {
                    is com.evyr.rebuild.data.BackupArchive.Result.Success -> {
                        folders.recordResult(true, "${r.rowCount} rows, ${r.mediaCount} media files")
                        message = "Saved — ${r.rowCount} rows, ${r.mediaCount} media files"
                        isError = false
                    }
                    is com.evyr.rebuild.data.BackupArchive.Result.Error -> {
                        folders.recordResult(false, r.message)
                        message = r.message
                        isError = true
                    }
                }
                lastStatus = folders.lastBackupStatus()
                lastAt = folders.lastBackupAtEpochMs()
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text("Weekly backup", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "Runs Tuesdays at 12:01 AM. Includes everything: all data plus notebook photos/videos and session clips. " +
                "The API key and PIN can't be included — they're tied to this device's keystore, so re-enter them after a restore.",
            fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
        )

        Spacer(Modifier.height(10.dp))
        Text(
            "FOLDER: ${folderLabel ?: "not set — weekly backup won't run"}",
            fontFamily = TerminalFontFamily,
            color = if (folderLabel == null) NervRed else NervGreen,
            fontSize = 10.sp
        )
        Text(
            "CHOOSE FOLDER",
            fontFamily = TerminalFontFamily,
            color = NervOrange,
            fontSize = 11.sp,
            modifier = Modifier
                .clickable { folderPicker.launch(null) }
                .padding(vertical = 6.dp)
        )

        if (lastAt > 0L) {
            Spacer(Modifier.height(6.dp))
            Text(
                "LAST: ${fmt.format(java.util.Date(lastAt))} · ${lastStatus ?: ""}",
                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                if (busy) "WORKING..." else "BACK UP NOW",
                fontFamily = TerminalFontFamily,
                color = if (busy) NervDim else NervGreen,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable(enabled = !busy) { runBackupNow() }
                    .padding(vertical = 4.dp)
            )
            Text(
                "RESTORE",
                fontFamily = TerminalFontFamily,
                color = NervOrange,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable(enabled = !busy) { restorePicker.launch(arrayOf("application/zip", "application/json")) }
                    .padding(vertical = 4.dp)
            )
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontFamily = TerminalFontFamily, color = if (isError) NervRed else NervGreen, fontSize = 10.sp)
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            containerColor = NervPanel,
            title = { Text("REPLACE ALL DATA?", fontFamily = TerminalFontFamily, color = NervRed) },
            text = {
                Text(
                    "Restoring replaces everything currently in the app with the contents of this backup. " +
                        "This can't be undone.",
                    fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    busy = true
                    message = null
                    scope.launch {
                        when (val r = viewModel.restoreArchive(context, uri)) {
                            is com.evyr.rebuild.data.BackupArchive.Result.Success -> {
                                message = "Restored ${r.rowCount} rows, ${r.mediaCount} media files — restart the app"
                                isError = false
                            }
                            is com.evyr.rebuild.data.BackupArchive.Result.Error -> {
                                message = r.message
                                isError = true
                            }
                        }
                        busy = false
                    }
                }) { Text("REPLACE", fontFamily = TerminalFontFamily, color = NervRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("CANCEL", fontFamily = TerminalFontFamily, color = NervDim)
                }
            }
        )
    }
}

