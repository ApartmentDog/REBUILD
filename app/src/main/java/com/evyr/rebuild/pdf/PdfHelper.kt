package com.evyr.rebuild.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Thin wrapper around Android's built-in android.graphics.pdf.PdfRenderer --
 * no third-party PDF library needed, this is part of the platform SDK
 * (available since API 21, well under this project's minSdk 26).
 *
 * Not a persistent/cached renderer: opens the file fresh for a page count
 * check or a single page render, then closes it. Simpler and safer than
 * managing a long-lived PdfRenderer instance across Compose recompositions,
 * at the cost of reopening the file descriptor per page turn -- an acceptable
 * trade for a personal reading app, not a high-frequency page-flip reader.
 */
object PdfHelper {

    fun pageCount(file: File): Int {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        } catch (e: Exception) {
            0
        }
    }

    /** Renders a single page to a Bitmap at the given target width, scaling
     *  height to preserve the page's aspect ratio. Returns null on failure
     *  (corrupted file, out-of-range page index, etc). */
    fun renderPage(file: File, pageIndex: Int, targetWidthPx: Int): Bitmap? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width
                        val targetHeightPx = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                        // White background first -- PDF pages assume an opaque
                        // white canvas; without this, transparent PDF regions
                        // render as black on some devices.
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
