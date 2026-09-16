package com.evyr.rebuild.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.evyr.rebuild.data.AttachmentKind
import com.evyr.rebuild.data.HiddenMediaItem
import com.evyr.rebuild.media.MediaCapture
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File

/**
 * Hidden section, reached by tapping the version footer four times and entering
 * the code. Holds a persistent library: files added here are copied into
 * app-private storage and stay listed until removed.
 *
 * Files live under filesDir/hidden, deliberately NOT filesDir/media. The backup
 * archive walks the media folder, so keeping these separate means they aren't
 * swept into a zip that gets written to a cloud-synced folder. The trade is that
 * they don't come back from a restore after a reinstall.
 */
@Composable
fun HiddenMediaPlayerScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val library by viewModel.hiddenMedia.collectAsState()

    var nowPlaying by remember { mutableStateOf<HiddenMediaItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var labelDraft by remember { mutableStateOf("") }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }
    var isSeeking by remember { mutableStateOf(false) }

    // Polls playback position while something is actually playing. MediaPlayer
    // has no position-changed callback, so polling is the standard approach --
    // paused while the user is dragging the slider so the drag isn't fought by
    // a stale position landing mid-gesture.
    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying && mediaPlayer != null) {
            if (!isSeeking) {
                positionMs = mediaPlayer?.currentPosition ?: 0
            }
            delay(500)
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) { pendingUri = uri; labelDraft = "" } }

    DisposableEffect(Unit) { onDispose { mediaPlayer?.release() } }

    fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        positionMs = 0
        durationMs = 0
    }

    Column(modifier = Modifier.fillMaxSize().background(EvaBackground)) {
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { stopPlayback(); onBack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EvaTextPrimary)
            }
            Text("///", fontFamily = TerminalFontFamily, color = NervMagenta, fontSize = 16.sp, letterSpacing = 4.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }) {
                Icon(Icons.Filled.LibraryAdd, contentDescription = "Add file", tint = NervMagenta)
            }
        }
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)

        nowPlaying?.let { item ->
            if (item.kind == AttachmentKind.VIDEO) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(item.filePath)
                            setOnPreparedListener { it.start() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(220.dp).background(EvaPanelBg)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EvaPanelBg)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            try {
                                val mp = mediaPlayer
                                if (mp == null) {
                                    mediaPlayer = MediaPlayer().apply {
                                        setDataSource(item.filePath)
                                        setOnCompletionListener { isPlaying = false; positionMs = 0 }
                                        prepare()
                                        start()
                                    }
                                    positionMs = 0
                                    durationMs = mediaPlayer?.duration ?: 0
                                    isPlaying = true
                                } else if (isPlaying) {
                                    mp.pause(); isPlaying = false
                                } else {
                                    mp.start(); isPlaying = true
                                }
                            } catch (e: Exception) {
                                stopPlayback()
                                error = "Couldn't play that file"
                            }
                        }) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = NervMagenta
                            )
                        }
                        Text(
                            item.label,
                            fontFamily = TerminalFontFamily,
                            color = EvaTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (mediaPlayer != null) {
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            onValueChange = { isSeeking = true; positionMs = it.toInt() },
                            onValueChangeFinished = {
                                mediaPlayer?.seekTo(positionMs)
                                isSeeking = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = NervMagenta,
                                activeTrackColor = NervMagenta,
                                inactiveTrackColor = EvaOutline
                            )
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatMs(positionMs), fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 10.sp)
                            Text(formatMs(durationMs), fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        error?.let {
            Text(it, fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
        }

        if (library.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "EMPTY — TAP + TO ADD FILES",
                    fontFamily = TerminalFontFamily,
                    color = EvaTextDim,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                items(library) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(EvaPanelBg, RoundedCornerShape(4.dp))
                            .clickable {
                                stopPlayback()
                                error = null
                                nowPlaying = item
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (item.kind == AttachmentKind.VIDEO) "▶" else "♪",
                            fontFamily = TerminalFontFamily,
                            color = NervMagenta,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            item.label,
                            fontFamily = TerminalFontFamily,
                            color = EvaTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (nowPlaying?.id == item.id) { stopPlayback(); nowPlaying = null }
                                viewModel.deleteHiddenMedia(item)
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = EvaTextDim, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    // Naming step after picking: the file is copied on confirm, so cancelling
    // leaves nothing behind in storage.
    pendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            containerColor = EvaPanelBg,
            title = { Text("ADD FILE", fontFamily = TerminalFontFamily, color = NervMagenta) },
            text = {
                OutlinedTextField(
                    value = labelDraft,
                    onValueChange = { labelDraft = it },
                    placeholder = { Text("Name it", fontFamily = TerminalFontFamily) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = EvaTextPrimary, unfocusedTextColor = EvaTextPrimary,
                        focusedBorderColor = NervMagenta, unfocusedBorderColor = EvaTextDim
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val mime = context.contentResolver.getType(uri) ?: ""
                    val isVideo = mime.startsWith("video/")
                    val ext = if (isVideo) "mp4" else "m4a"
                    val path = MediaCapture.copyPickedFileIntoStorage(context, uri, "hidden", ext)
                    if (path != null) {
                        viewModel.addHiddenMedia(
                            label = labelDraft.ifBlank { File(path).name },
                            filePath = path,
                            // AttachmentKind has no AUDIO case; PHOTO is reused as
                            // "not video" here purely to drive playback selection
                            // (MediaPlayer vs VideoView), not to mean an image.
                            kind = if (isVideo) AttachmentKind.VIDEO else AttachmentKind.PHOTO
                        )
                    } else {
                        error = "Couldn't copy that file"
                    }
                    pendingUri = null
                }) { Text("ADD", fontFamily = TerminalFontFamily, color = NervMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUri = null }) {
                    Text("CANCEL", fontFamily = TerminalFontFamily, color = EvaTextDim)
                }
            }
        )
    }
}

/** Formats milliseconds as M:SS (or H:MM:SS past an hour), for the seek bar's
 *  position/duration labels. */
private fun formatMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
