package io.github.jayyo.readeverything.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.jayyo.readeverything.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

private const val MAX_PAGE_PIXELS = 8_000_000   // ~32 MB per ARGB bitmap
private const val MAX_ZOOM = 4f

/**
 * PdfRenderer is not thread-safe and renders one page at a time, so opening,
 * rendering and closing all go through one mutex, on a background thread.
 */
private class PdfSource private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    private val mutex = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount

    /** Width / height of pages rendered so far; others use A4 until drawn. */
    val ratios = java.util.concurrent.ConcurrentHashMap<Int, Float>()

    suspend fun render(index: Int, targetWidthPx: Int): Bitmap? = mutex.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                ratios[index] = page.width.toFloat() / page.height.toFloat()
                var w = targetWidthPx.coerceAtLeast(1)
                var h = (w.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                val pixels = w.toLong() * h
                if (pixels > MAX_PAGE_PIXELS) {
                    val f = sqrt(MAX_PAGE_PIXELS.toDouble() / pixels)
                    w = (w * f).toInt().coerceAtLeast(1)
                    h = (h * f).toInt().coerceAtLeast(1)
                }
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    fun closeLater() {
        closeScope.launch {
            mutex.withLock {
                closed = true
                runCatching { renderer.close() }   // also closes the descriptor
            }
        }
    }

    companion object {
        private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun open(file: File): PdfSource {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(pfd)
            } catch (e: Exception) {
                pfd.close()
                throw e
            }
            return PdfSource(pfd, renderer)
        }
    }
}

private sealed interface Opened {
    object Loading : Opened
    data class Ok(val source: PdfSource) : Opened
    data class Failed(val message: String) : Opened
}

@Composable
fun PdfScreen(file: File, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val opened by produceState<Opened>(Opened.Loading, file.path) {
        // NonCancellable: once opened, the renderer must reach the close below
        // even if the screen goes away while it was opening.
        val source = try {
            withContext(Dispatchers.IO + NonCancellable) { PdfSource.open(file) }
        } catch (e: SecurityException) {
            value = Opened.Failed(ctx.getString(R.string.err_pdf_locked))
            return@produceState
        } catch (e: Exception) {
            value = Opened.Failed(ctx.getString(R.string.err_pdf, e.message ?: e.javaClass.simpleName))
            return@produceState
        }
        try {
            value = Opened.Ok(source)
            awaitDispose { source.closeLater() }
        } catch (e: Throwable) {
            source.closeLater()
            throw e
        }
    }

    when (val o = opened) {
        is Opened.Loading -> CenterLoading()
        is Opened.Failed -> CenterMessage(o.message)
        is Opened.Ok -> ZoomablePages(o.source, modifier)
    }
}

@Composable
private fun ZoomablePages(source: PdfSource, modifier: Modifier) {
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    val hScroll = rememberScrollState()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFFD9D9D9))
            // Pinch only: one finger stays with the scroll containers below.
            .pointerInput(source) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val z = event.calculateZoom()
                            if (z != 1f) {
                                zoom = (zoom * z).coerceIn(1f, MAX_ZOOM)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val pageWidth = maxWidth * zoom
        Box(Modifier.fillMaxSize().horizontalScroll(hScroll, enabled = zoom > 1f)) {
            LazyColumn(Modifier.width(pageWidth).fillMaxSize()) {
                items(source.pageCount) { index -> PdfPage(source, index) }
            }
        }
    }
}

@Composable
private fun PdfPage(source: PdfSource, index: Int) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Render at the on-screen width, rounded so small zoom steps reuse the bitmap.
        val widthPx = with(density) { maxWidth.roundToPx() }.let { (it + 63) / 64 * 64 }
        val bmp by produceState<Result<Bitmap?>?>(null, source, index, widthPx) {
            value = try {
                Result.success(source.render(index, widthPx))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val ratio = source.ratios[index] ?: (1f / 1.4142f)
        val r = bmp
        when {
            r == null || (r.isSuccess && r.getOrNull() == null) -> Box(
                Modifier.fillMaxWidth().aspectRatio(ratio).background(androidx.compose.ui.graphics.Color.White),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            r.isFailure -> Box(
                Modifier.fillMaxWidth().aspectRatio(ratio).background(androidx.compose.ui.graphics.Color.White),
                contentAlignment = Alignment.Center,
            ) { Text(ctx.getString(R.string.err_page, index + 1)) }
            else -> Image(
                bitmap = r.getOrThrow()!!.asImageBitmap(),
                contentDescription = ctx.getString(R.string.page_n, index + 1),
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}
