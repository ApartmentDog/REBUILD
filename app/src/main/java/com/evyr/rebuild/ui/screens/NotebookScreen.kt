package com.evyr.rebuild.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.evyr.rebuild.data.AttachmentKind
import com.evyr.rebuild.data.NotebookEntry
import com.evyr.rebuild.media.MediaCapture
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.components.HexGridBackground
import com.evyr.rebuild.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotebookScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val entries by viewModel.notebookResults.collectAsState()
    val query by viewModel.notebookQuery.collectAsState()
    var draft by remember { mutableStateOf("") }
    var tagsDraft by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }

    HexGridBackground(modifier = Modifier.fillMaxSize().background(NervBackground)) {
    Column(modifier = Modifier.fillMaxSize()) {
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NervGreen)
            }
            Text(
                text = "NOTEBOOK",
                fontFamily = TerminalFontFamily,
                color = NervOrange,
                fontSize = 16.sp,
                letterSpacing = 2.sp
            )
        }
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setNotebookQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search entries or #tags...", fontFamily = TerminalFontFamily, color = NervDim) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NervDim) },
            singleLine = true,
            colors = rebuildTextFieldColors()
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            reverseLayout = false
        ) {
            items(entries) { entry ->
                NotebookEntryRow(
                    entry = entry,
                    fmt = fmt,
                    viewModel = viewModel,
                    onDelete = { viewModel.deleteNotebookEntry(entry) }
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = tagsDraft,
                onValueChange = { tagsDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("tags, comma, separated (optional)", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NervDim,
                    unfocusedTextColor = NervDim,
                    focusedBorderColor = NervOrange,
                    unfocusedBorderColor = NervDim
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("New entry...", fontFamily = TerminalFontFamily, color = NervDim) },
                    colors = rebuildTextFieldColors()
                )
                IconButton(onClick = {
                    val parsedTags = tagsDraft.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    viewModel.addNotebookEntry(draft, parsedTags)
                    draft = ""
                    tagsDraft = ""
                }) {
                    Icon(Icons.Filled.Send, contentDescription = "Add entry", tint = NervOrange)
                }
            }
        }
    }
    }
}

@Composable
private fun NotebookEntryRow(entry: NotebookEntry, fmt: SimpleDateFormat, viewModel: MainViewModel, onDelete: () -> Unit) {
    val context = LocalContext.current
    val attachments by viewModel.observeMediaForNotebookEntry(entry.id).collectAsState()

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoFile by remember { mutableStateOf<java.io.File?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingPhotoFile?.let { file ->
                viewModel.addMediaAttachment(AttachmentKind.PHOTO, file.absolutePath, notebookEntryId = entry.id)
            }
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            pendingVideoFile?.let { file ->
                viewModel.addMediaAttachment(AttachmentKind.VIDEO, file.absolutePath, notebookEntryId = entry.id)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ENTRY — ${fmt.format(Date(entry.createdAtEpochMs))}",
                fontFamily = TerminalFontFamily,
                color = NervDim,
                fontSize = 10.sp
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = NervDim, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.text,
            fontFamily = TerminalFontFamily,
            color = NervGreen,
            fontSize = 13.sp
        )

        if (entry.tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.tags.forEach { tag ->
                    Text(
                        "#$tag",
                        fontFamily = TerminalFontFamily,
                        color = NervOrange,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable { viewModel.setNotebookQuery(tag) }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }

        if (attachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(attachments) { media ->
                    if (media.kind == AttachmentKind.PHOTO) {
                        AsyncImage(
                            model = media.filePath,
                            contentDescription = "Attached photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).background(NervBackground, RoundedCornerShape(4.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(NervBackground, RoundedCornerShape(4.dp)),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Icon(Icons.Filled.Videocam, contentDescription = "Attached video", tint = NervGreen)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Android blocks ACTION_IMAGE_CAPTURE / ACTION_VIDEO_CAPTURE for an app that
        // declares CAMERA in its manifest but hasn't been granted it at runtime,
        // which makes these buttons silently fall through to a file picker instead
        // of opening the camera. Request the permission first.
        fun launchPhoto() {
            val (file, uri) = MediaCapture.createPhotoUri(context)
            pendingPhotoFile = file
            pendingPhotoUri = uri
            photoLauncher.launch(uri)
        }
        fun launchVideo() {
            val (file, uri) = MediaCapture.createVideoUri(context)
            pendingVideoFile = file
            pendingVideoUri = uri
            videoLauncher.launch(uri)
        }

        var pendingCaptureIsVideo by remember { mutableStateOf(false) }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                if (pendingCaptureIsVideo) launchVideo() else launchPhoto()
            }
        }
        fun startCapture(isVideo: Boolean) {
            pendingCaptureIsVideo = isVideo
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                if (isVideo) launchVideo() else launchPhoto()
            } else {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(
                onClick = { startCapture(isVideo = false) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Add photo", tint = NervDim, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { startCapture(isVideo = true) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = "Add video", tint = NervDim, modifier = Modifier.size(16.dp))
            }
        }
    }
}
