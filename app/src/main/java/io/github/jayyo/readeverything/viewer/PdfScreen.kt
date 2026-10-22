package io.github.jayyo.readeverything.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Wraps PdfRenderer with a mutex since it is not thread-safe and renders one page at a time. */
private class PdfSource(file: File) {
    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val mutex = Mutex()
    val pageCount: Int get() = renderer.pageCount

    suspend fun render(index: Int, targetWidthPx: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val ratio = page.height.toFloat() / page.width.toFloat()
                val w = targetWidthPx.coerceIn(1, 2400)
                val h = (w * ratio).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
fun PdfScreen(file: File, modifier: Modifier = Modifier) {
    var source by remember { mutableStateOf<PdfSource?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(file.path) {
        try {
            source = PdfSource(file)
        } catch (e: Exception) {
            error = e.message
        }
        onDispose { source?.close() }
    }

    val s = source
    when {
        error != null -> CenterMessage("PDF 열기 실패: $error")
        s == null -> CenterLoading()
        else -> LazyColumn(
            modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFFD9D9D9)),
        ) {
            items(s.pageCount) { index -> PdfPage(s, index) }
        }
    }
}

@Composable
private fun PdfPage(source: PdfSource, index: Int) {
    var widthPx by remember { mutableStateOf(0) }
    val bmp by produceState<Bitmap?>(null, source, index, widthPx) {
        value = if (widthPx > 0) runCatching { source.render(index, widthPx) }.getOrNull() else null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .onSizeChanged { widthPx = it.width },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "page ${index + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(420.dp).background(androidx.compose.ui.graphics.Color.White),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }
    }
}
