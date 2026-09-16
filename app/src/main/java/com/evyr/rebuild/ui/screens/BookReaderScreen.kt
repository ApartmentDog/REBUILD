package com.evyr.rebuild.ui.screens

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.evyr.rebuild.data.BookFormat
import com.evyr.rebuild.data.BookItem
import com.evyr.rebuild.epub.EpubParser
import com.evyr.rebuild.pdf.PdfHelper
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reader for a single book, opened at its last saved position. Branches
 * entirely on BookItem.format:
 *  - PDF: renders one page at a time as a Bitmap via PdfHelper (platform
 *    PdfRenderer), with prev/next controls.
 *  - EPUB: loads one chapter's raw XHTML into a WebView per screen, using
 *    WebView's own HTML/CSS rendering rather than hand-building one --
 *    chapters are the unit of navigation, not scroll position within one.
 *
 * Position is persisted (page index for PDF, chapter index for EPUB)
 * whenever it changes, via MainViewModel.updateBookPosition.
 */
@Composable
fun BookReaderScreen(viewModel: MainViewModel, book: BookItem, onBack: () -> Unit) {
    var position by remember(book.id) { mutableStateOf(book.lastPosition) }

    LaunchedEffect(position) {
        viewModel.updateBookPosition(book, position)
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
                book.title,
                fontFamily = TerminalFontFamily, color = NervMagenta, fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)

        Box(modifier = Modifier.weight(1f)) {
            if (book.format == BookFormat.PDF) {
                PdfPageView(book = book, pageIndex = position)
            } else {
                EpubChapterView(book = book, chapterIndex = position)
            }
        }

        // Shared prev/next + position indicator footer for both formats.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EvaPanelBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (position > 0) position -= 1 },
                enabled = position > 0
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous", tint = NervMagenta)
            }
            Text(
                if (book.format == BookFormat.PDF) "Page ${position + 1} / ${book.totalPages}"
                else "Chapter ${position + 1} / ${book.totalPages}",
                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(
                onClick = { if (position < book.totalPages - 1) position += 1 },
                enabled = position < book.totalPages - 1
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next", tint = NervMagenta)
            }
        }
    }
}

@Composable
private fun PdfPageView(book: BookItem, pageIndex: Int) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(book.id, pageIndex) {
        bitmap = null
        error = null
        val widthPx = with(density) { screenWidthDp.dp.toPx() }.toInt()
        val result = withContext(Dispatchers.IO) {
            PdfHelper.renderPage(File(book.filePath), pageIndex, widthPx)
        }
        if (result == null) error = "Couldn't render this page" else bitmap = result
    }

    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(error!!, fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 12.sp)
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth()
            )
            else -> CircularProgressIndicator(color = NervMagenta)
        }
    }
}

@Composable
private fun EpubChapterView(book: BookItem, chapterIndex: Int) {
    val context = LocalContext.current
    var html by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(book.id, chapterIndex) {
        html = null
        error = null
        val result = withContext(Dispatchers.IO) {
            val parsed = EpubParser.parse(File(book.filePath))
            val href = parsed?.chapterHrefs?.getOrNull(chapterIndex)
            if (parsed == null || href == null) null
            else EpubParser.readChapter(File(book.filePath), href)
        }
        if (result == null) error = "Couldn't load this chapter" else html = result
    }

    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
        when {
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 12.sp)
            }
            html != null -> AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                    }
                },
                update = { webView ->
                    // baseUrl null: chapter HTML may reference images/CSS by
                    // relative path that won't resolve without the zip's
                    // internal structure served as a real origin -- out of
                    // scope for this reader. Text content renders regardless.
                    webView.loadDataWithBaseURL(null, html!!, "text/html", "utf-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NervMagenta)
            }
        }
    }
}
