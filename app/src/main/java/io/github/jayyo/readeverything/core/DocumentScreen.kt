package io.github.jayyo.readeverything.core

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.jayyo.readeverything.R
import io.github.jayyo.readeverything.convert.RtfText
import io.github.jayyo.readeverything.viewer.CenterLoading
import io.github.jayyo.readeverything.viewer.CenterMessage
import io.github.jayyo.readeverything.viewer.CodeScreen
import io.github.jayyo.readeverything.viewer.ConvertThenPdfScreen
import io.github.jayyo.readeverything.viewer.CsvScreen
import io.github.jayyo.readeverything.viewer.ImageScreen
import io.github.jayyo.readeverything.viewer.JsonScreen
import io.github.jayyo.readeverything.viewer.MarkdownScreen
import io.github.jayyo.readeverything.viewer.PdfScreen
import io.github.jayyo.readeverything.viewer.TextScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/** A loaded document, ready for its renderer. Loading happens off the main thread. */
sealed interface Prepared {
    val title: String

    data class Pdf(override val title: String, val file: File) : Prepared
    data class Img(override val title: String, val uri: Uri) : Prepared
    data class Markdown(override val title: String, val text: String) : Prepared
    data class Json(override val title: String, val text: String) : Prepared
    data class Code(override val title: String, val text: String, val ext: String) : Prepared
    data class PlainText(override val title: String, val text: String, val mono: Boolean) : Prepared
    data class Table(override val title: String, val text: String, val tab: Boolean) : Prepared
    class Convert(override val title: String, val bytes: ByteArray, val sha: String,
                  val ext: String, val label: String) : Prepared
    data class Unsupported(override val title: String, val label: String) : Prepared
}

// Size limits per renderer. Everything is held in memory as a String or ByteArray.
private const val MB = 1024L * 1024
private const val MAX_TEXT = 16 * MB
private const val MAX_MARKDOWN = 4 * MB
private const val MAX_JSON_TREE = 4 * MB
private const val MAX_UPLOAD = 48 * MB      // the server's default request limit is 50 MB
private const val MAX_PDF = 512 * MB

private val CODE_EXTS = setOf(
    "kt", "kts", "java", "py", "js", "mjs", "ts", "tsx", "jsx", "c", "h", "cpp", "hpp", "cc",
    "cs", "go", "rs", "rb", "php", "swift", "sh", "bash", "yml", "yaml", "toml", "gradle",
    "xml", "html", "css", "scss", "sql", "dart", "lua", "pl", "r", "m", "scala",
)

suspend fun prepareDocument(ctx: Context, uri: Uri): Prepared = withContext(Dispatchers.IO) {
    val resolver = ctx.contentResolver
    val name = FileUtils.displayName(resolver, uri)
    fun text(limit: Long) = FileUtils.decodeText(FileUtils.readBytes(resolver, uri, limit))
    when (val type = TypeDetector.detect(resolver, uri, name)) {
        is DocType.Pdf -> Prepared.Pdf(name, FileUtils.copyToCache(resolver, uri, ctx.cacheDir, MAX_PDF) { ensureActive() })
        is DocType.Image -> Prepared.Img(name, uri)
        is DocType.Markdown -> Prepared.Markdown(name, text(MAX_MARKDOWN))
        is DocType.Json -> {
            val raw = text(MAX_TEXT)
            if (raw.length <= MAX_JSON_TREE && isLikelyValidJson(raw)) Prepared.Json(name, raw)
            else Prepared.PlainText(name, prettyJsonOrRaw(raw), true)
        }
        is DocType.Text -> {
            val txt = text(MAX_TEXT)
            if (type.sub in CODE_EXTS) Prepared.Code(name, txt, type.sub)
            else Prepared.PlainText(name, txt, false)
        }
        is DocType.Csv -> Prepared.Table(name, text(MAX_TEXT), name.endsWith(".tsv", true))
        is DocType.Rtf ->
            Prepared.PlainText(name, RtfText.extract(FileUtils.readBytes(resolver, uri, MAX_TEXT)), false)
        is DocType.Office, is DocType.Hwp, is DocType.Hwpx -> {
            val bytes = FileUtils.readBytes(resolver, uri, MAX_UPLOAD)
            Prepared.Convert(name, bytes, FileUtils.sha256(bytes), type.conversionExt!!, type.label)
        }
        is DocType.Epub -> Prepared.Unsupported(name, "EPUB")
        is DocType.Unknown -> Prepared.Unsupported(name, type.mime ?: "")
    }
}

private fun isLikelyValidJson(s: String): Boolean = runCatching {
    val t = s.trim()
    when {
        t.startsWith("{") -> { org.json.JSONObject(t); true }
        t.startsWith("[") -> { org.json.JSONArray(t); true }
        else -> false
    }
}.getOrDefault(false)

private fun prettyJsonOrRaw(s: String): String = runCatching {
    val t = s.trimStart()
    when {
        t.startsWith("{") -> org.json.JSONObject(s).toString(2)
        t.startsWith("[") -> org.json.JSONArray(s).toString(2)
        else -> s
    }
}.getOrDefault(s)

private fun describe(ctx: Context, e: Throwable): String = when (e) {
    is TooLargeException -> ctx.getString(R.string.err_too_large, e.limit / MB)
    is SecurityException -> ctx.getString(R.string.err_permission)
    is FileNotFoundException -> ctx.getString(R.string.err_gone)
    else -> ctx.getString(R.string.err_open, e.message ?: e.javaClass.simpleName)
}

@Composable
fun DocumentHost(uri: Uri, onTitle: (String) -> Unit = {}) {
    val ctx: Context = LocalContext.current
    // Keyed by the URI, so a new document never shows the previous one's state.
    var prepared by remember(uri) { mutableStateOf<Prepared?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        try {
            prepared = prepareDocument(ctx, uri).also { onTitle(it.title) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = describe(ctx, e)
        }
    }

    val p = prepared
    when {
        error != null -> CenterMessage(error!!)
        p == null -> CenterLoading(ctx.getString(R.string.loading_document))
        else -> Router(p)
    }
}

@Composable
private fun Router(p: Prepared) {
    when (p) {
        is Prepared.Pdf -> PdfScreen(p.file)
        is Prepared.Img -> ImageScreen(p.uri)
        is Prepared.Markdown -> MarkdownScreen(p.text)
        is Prepared.Json -> JsonScreen(p.text)
        is Prepared.Code -> CodeScreen(p.text, p.ext)
        is Prepared.PlainText -> TextScreen(p.text, p.mono)
        is Prepared.Table -> CsvScreen(p.text, p.tab)
        is Prepared.Convert -> ConvertThenPdfScreen(p)
        is Prepared.Unsupported -> io.github.jayyo.readeverything.viewer.UnsupportedScreen(p.label)
    }
}
