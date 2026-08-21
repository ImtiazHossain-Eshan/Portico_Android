package com.portico.android.ui.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * In-app PDF viewing.
 *
 * Android ships PdfRenderer, so a lease can be read inside Portico rather than
 * handed to whatever app happens to claim application/pdf. That matters here
 * more than usual: these documents are private, and passing a content URI to a
 * third-party viewer is the one moment the product loses custody of them.
 *
 * Pages render lazily. A twelve-page lease at full resolution is tens of
 * megabytes of bitmap, so each page is rasterised only as it scrolls into view
 * and released when it leaves.
 */

private const val RENDER_WIDTH = 1_400

data class PdfPage(val index: Int, val ratio: Float)

class PdfDocumentHandle(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : AutoCloseable {
    val pages: List<PdfPage> = (0 until renderer.pageCount).map { index ->
        renderer.openPage(index).use { page ->
            PdfPage(index, page.width.toFloat() / page.height.toFloat())
        }
    }

    /**
     * Rasterises one page.
     *
     * PdfRenderer allows a single open page at a time across the whole
     * renderer, so this is synchronised; two composables scrolling into view
     * together would otherwise throw.
     */
    @Synchronized
    fun render(index: Int): Bitmap? = runCatching {
        renderer.openPage(index).use { page ->
            val height = (RENDER_WIDTH * page.height / page.width).coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(RENDER_WIDTH, height, Bitmap.Config.ARGB_8888)
            // PDFs assume paper. Without this the transparent areas render
            // black in dark mode and the document becomes unreadable.
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }.getOrNull()

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

suspend fun openPdf(context: Context, uri: Uri): Result<PdfDocumentHandle> =
    withContext(Dispatchers.IO) {
        runCatching {
            val descriptor = if (uri.scheme == "file") {
                ParcelFileDescriptor.open(
                    File(requireNotNull(uri.path)),
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
            } else {
                requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")) {
                    "This document could not be opened"
                }
            }
            PdfDocumentHandle(descriptor, PdfRenderer(descriptor))
        }
    }

@Composable
fun PdfViewer(handle: PdfDocumentHandle, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        items(handle.pages, key = { it.index }) { page ->
            PdfPageView(handle, page)
        }
        item {
            Box(Modifier.fillMaxWidth().padding(Space.md), contentAlignment = Alignment.Center) {
                SectionLabel("${handle.pages.size} page${if (handle.pages.size == 1) "" else "s"}")
            }
        }
    }
}

@Composable
private fun PdfPageView(handle: PdfDocumentHandle, page: PdfPage) {
    val semantic = PorticoTheme.semantic
    var bitmap by remember(page.index) { mutableStateOf<Bitmap?>(null) }

    /*
     * Rasterise on entry, release on exit, so the list holds only what is on
     * screen. The reference is dropped rather than recycled: a recycled bitmap
     * that a composition is still drawing crashes the canvas, and letting the
     * collector take it is both safe and immediate enough here.
     */
    DisposableEffect(page.index) {
        onDispose { bitmap = null }
    }
    LaunchedEffect(page.index) {
        bitmap = withContext(Dispatchers.IO) { handle.render(page.index) }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(page.ratio.coerceIn(0.3f, 3f))
            .background(semantic.panelSunk)
            .border(1.dp, semantic.hairline)
            .semantics { contentDescription = "Page ${page.index + 1}" },
        contentAlignment = Alignment.Center
    ) {
        val rendered = bitmap
        if (rendered == null) {
            SectionLabel("Page ${page.index + 1}")
        } else {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
