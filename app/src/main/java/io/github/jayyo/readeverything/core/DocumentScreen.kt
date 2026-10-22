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
import io.github.jayyo.readeverything.convert.RtfText
import io.github.jayyo.readeverything.viewer.CenterLoading
import io.github.jayyo.readeverything.viewer.CenterMessage
import io.github.jayyo.readeverything.viewer.ConvertThenPdfScreen
import io.github.jayyo.readeverything.viewer.CodeScreen
import io.github.jayyo.readeverything.viewer.CsvScreen
import io.github.jayyo.readeverything.viewer.ImageScreen
import io.github.jayyo.readeverything.viewer.JsonScreen
import io.github.jayyo.readeverything.viewer.MarkdownScreen
import io.github.jayyo.readeverything.viewer.PdfScreen
import io.github.jayyo.readeverything.viewer.TextScreen
import io.github.jayyo.readeverything.viewer.UnsupportedScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A fully-loaded document, ready for its renderer. Loading/IO happens off the main thread. */
sealed interface Prepared {
    val title: String

    data class Pdf(override val title: String, val file: File) : Prepared
    data class Img(override val title: String, val uri: Uri) : Prepared
    data class Markdown(override val title: String, val text: String) : Prepared
    data class Json(override val title: String, val text: String) : Prepared
    data class Code(override val title: String, val text: String, val ext: String) : Prepared
    data class PlainText(override val title: String, val text: String, val mono: Boolean) : Prepared
    data class Table(override val title: String, val text: String, val tab: Boolean) : Prepared
    data class Convert(override val title: String, val bytes: ByteArray, val label: String) : Prepared
    data class Unsupported(override val title: String, val label: String) : Prepared
}

private val CODE_EXTS = setOf(
    "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "c", "h", "cpp", "hpp", "cc",
    "go", "rs", "rb", "php", "swift", "sh", "bash", "yml", "yaml", "toml", "gradle",
    "xml", "html", "css", "scss", "sql", "dart", "lua", "pl", "r", "m", "scala",
)

suspend fun prepareDocument(ctx: Context, uri: Uri): Prepared = withContext(Dispatchers.IO) {
    val resolver = ctx.contentResolver
    val name = FileUtils.displayName(resolver, uri)
    when (val type = TypeDetector.detect(resolver, uri, name)) {
        is DocType.Pdf ->
            Prepared.Pdf(name, FileUtils.copyToCache(resolver, uri, ctx.cacheDir, name.ifEmpty { "doc.pdf" }))
        is DocType.Image -> Prepared.Img(name, uri)
        is DocType.Markdown ->
            Prepared.Markdown(name, FileUtils.decodeText(FileUtils.readBytes(resolver, uri)))
        is DocType.Json -> {
            val raw = FileUtils.decodeText(FileUtils.readBytes(resolver, uri))
            if (isLikelyValidJson(raw)) Prepared.Json(name, raw)
            else Prepared.PlainText(name, prettyJsonOrRaw(raw), true)
        }
        is DocType.Text -> {
            val txt = FileUtils.decodeText(FileUtils.readBytes(resolver, uri))
            if (type.sub in CODE_EXTS) Prepared.Code(name, txt, type.sub)
            else Prepared.PlainText(name, txt, false)
        }
        is DocType.Csv ->
            Prepared.Table(name, FileUtils.decodeText(FileUtils.readBytes(resolver, uri)), name.endsWith(".tsv", true))
        is DocType.Rtf ->
            Prepared.PlainText(name, RtfText.extract(FileUtils.readBytes(resolver, uri)), false)
        is DocType.Office -> Prepared.Convert(name, FileUtils.readBytes(resolver, uri), type.label)
        is DocType.Hwp -> Prepared.Convert(name, FileUtils.readBytes(resolver, uri), "HWP")
        is DocType.Hwpx -> Prepared.Convert(name, FileUtils.readBytes(resolver, uri), "HWPX")
        is DocType.Epub -> Prepared.Unsupported(name, "EPUB")
        is DocType.Unknown -> Prepared.Unsupported(name, type.mime ?: "알 수 없음")
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

@Composable
fun DocumentHost(uri: Uri) {
    val ctx: Context = LocalContext.current
    var prepared by remember { mutableStateOf<Prepared?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        runCatching { prepareDocument(ctx, uri) }
            .onSuccess { prepared = it }
            .onFailure { error = it.message ?: it.toString() }
    }

    val p = prepared
    when {
        error != null -> CenterMessage("열기 실패: $error")
        p == null -> CenterLoading("문서 분석 중…")
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
        is Prepared.Convert -> ConvertThenPdfScreen(p.bytes, p.title, p.label)
        is Prepared.Unsupported -> UnsupportedScreen(p.label)
    }
}
