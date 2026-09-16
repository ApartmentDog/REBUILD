package com.evyr.rebuild.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.BookFormat
import com.evyr.rebuild.data.BookItem
import com.evyr.rebuild.epub.EpubParser
import com.evyr.rebuild.media.MediaCapture
import com.evyr.rebuild.pdf.PdfHelper
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import java.io.File

/**
 * Reached via the ALEXANDRIA cheat code. Library of imported books (copied
 * into filesDir/books on add, same pattern as Hidden Media) with format
 * auto-detected from the picked file's extension. Tapping a book opens
 * BookReaderScreen at its last saved position.
 */
@Composable
fun BookLibraryScreen(viewModel: MainViewModel, onBack: () -> Unit, onOpenBook: (BookItem) -> Unit) {
    val context = LocalContext.current
    val books by viewModel.books.collectAsState()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var titleDraft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
            titleDraft = ""
            error = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(EvaBackground)) {
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EvaTextPrimary)
            }
            Text(
                "ALEXANDRIA",
                fontFamily = TerminalFontFamily, color = NervMagenta, fontSize = 15.sp, letterSpacing = 3.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { picker.launch(arrayOf("application/pdf", "application/epub+zip", "application/octet-stream")) }) {
                Icon(Icons.Filled.LibraryAdd, contentDescription = "Add book", tint = NervMagenta)
            }
        }
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)

        error?.let {
            Text(it, fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
        }

        if (isImporting) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NervMagenta)
            }
        }

        if (books.isEmpty() && !isImporting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "EMPTY — TAP + TO ADD A PDF OR EPUB",
                    fontFamily = TerminalFontFamily,
                    color = EvaTextDim,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                items(books) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(EvaPanelBg, RoundedCornerShape(4.dp))
                            .clickable { onOpenBook(book) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 13.sp)
                            val progressLabel = if (book.format == BookFormat.PDF) {
                                if (book.totalPages > 0) "Page ${book.lastPosition + 1} of ${book.totalPages}" else "PDF"
                            } else {
                                if (book.totalPages > 0) "Chapter ${book.lastPosition + 1} of ${book.totalPages}" else "EPUB"
                            }
                            Text(progressLabel, fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp)
                        }
                        IconButton(onClick = {
                            File(book.filePath).delete()
                            viewModel.deleteBook(book)
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete book", tint = NervRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    pendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!isImporting) pendingUri = null },
            containerColor = EvaPanelBg,
            title = { Text("ADD BOOK", fontFamily = TerminalFontFamily, color = NervMagenta) },
            text = {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    placeholder = { Text("Title (optional)", fontFamily = TerminalFontFamily) },
                    enabled = !isImporting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = EvaTextPrimary, unfocusedTextColor = EvaTextPrimary,
                        focusedBorderColor = NervMagenta, unfocusedBorderColor = EvaTextDim
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        val mime = context.contentResolver.getType(uri) ?: ""
                        // MIME alone isn't reliable for EPUB -- many pickers/devices
                        // report it as application/octet-stream or application/zip
                        // instead of application/epub+zip. Falling back to the
                        // filename's extension (queried via the content resolver,
                        // since content:// URIs don't expose a path) catches those.
                        val queriedName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                        } ?: ""
                        val lowerName = queriedName.lowercase()
                        val isPdf = mime == "application/pdf" || lowerName.endsWith(".pdf")
                        val isEpub = !isPdf && (mime == "application/epub+zip" || lowerName.endsWith(".epub"))
                        if (!isPdf && !isEpub) {
                            error = "Not a recognized PDF or EPUB file"
                            pendingUri = null
                            return@TextButton
                        }
                        isImporting = true
                        val ext = if (isPdf) "pdf" else "epub"
                        val path = MediaCapture.copyPickedFileIntoStorage(context, uri, "books", ext)
                        if (path == null) {
                            error = "Couldn't copy that file"
                            isImporting = false
                            pendingUri = null
                            return@TextButton
                        }
                        val file = File(path)
                        if (isPdf) {
                            val pageCount = PdfHelper.pageCount(file)
                            if (pageCount == 0) {
                                error = "Couldn't read that PDF"
                                file.delete()
                            } else {
                                viewModel.addBook(
                                    title = titleDraft.ifBlank { file.nameWithoutExtension },
                                    filePath = path,
                                    format = BookFormat.PDF,
                                    totalPages = pageCount
                                )
                            }
                        } else {
                            val parsed = EpubParser.parse(file)
                            if (parsed == null || parsed.chapterHrefs.isEmpty()) {
                                error = "Couldn't read that EPUB"
                                file.delete()
                            } else {
                                viewModel.addBook(
                                    title = titleDraft.ifBlank { parsed.title.ifBlank { file.nameWithoutExtension } },
                                    filePath = path,
                                    format = BookFormat.EPUB,
                                    totalPages = parsed.chapterHrefs.size
                                )
                            }
                        }
                        isImporting = false
                        pendingUri = null
                    }
                ) { Text("ADD", fontFamily = TerminalFontFamily, color = NervMagenta) }
            },
            dismissButton = {
                TextButton(enabled = !isImporting, onClick = { pendingUri = null }) {
                    Text("CANCEL", fontFamily = TerminalFontFamily, color = EvaTextDim)
                }
            }
        )
    }
}
