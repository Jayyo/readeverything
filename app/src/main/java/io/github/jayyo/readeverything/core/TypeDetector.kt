package io.github.jayyo.readeverything.core

import android.content.ContentResolver
import android.net.Uri
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Works out what a file is from its first bytes, falling back to the extension.
 * content:// URIs often arrive as application/octet-stream, and HWPX, DOCX, PPTX,
 * XLSX, ODF and EPUB all start with the same ZIP magic, so ZIP files are opened
 * and their entry names looked at. Best effort: the extension decides when the
 * bytes don't.
 */
object TypeDetector {
    private const val PEEK = 16 * 1024          // enough for the HWP 5 header string
    private const val MAX_ZIP_ENTRIES = 200
    private const val MAX_ZIP_BYTES = 8L * 1024 * 1024   // inflated bytes we are willing to read

    data class ZipInfo(val names: List<String>, val mimetype: String?)

    fun detect(resolver: ContentResolver, uri: Uri, name: String): DocType {
        val head = FileUtils.readHead(resolver, uri, PEEK)
        val zip = if (isZip(head)) {
            runCatching { resolver.openInputStream(uri)?.use { scanZip(it) } }.getOrNull()
        } else null
        return detect(head, FileUtils.extensionOf(name), zip)
    }

    /** Pure part of [detect], for tests. */
    fun detect(head: ByteArray, ext: String, zip: ZipInfo?): DocType {
        if (startsWith(head, "%PDF")) return DocType.Pdf
        if (isImage(head)) return DocType.Image
        if (isOle2(head)) return ole2(head, ext)
        if (isZip(head)) return zipType(zip, ext)
        if (startsWith(head, "{\\rtf")) return DocType.Rtf
        return byExtension(ext)
            ?: if (looksTextual(head)) DocType.Text(ext.ifEmpty { "txt" }) else DocType.Unknown(null)
    }

    private fun byExtension(ext: String): DocType? = when (ext) {
        "md", "markdown" -> DocType.Markdown
        "json" -> DocType.Json
        "csv", "tsv" -> DocType.Csv
        "rtf" -> DocType.Rtf
        "pdf" -> DocType.Pdf
        "hwp" -> DocType.Hwp
        "hwpx" -> DocType.Hwpx
        "doc", "docx", "ppt", "pptx", "xls", "xlsx", "odt", "odp", "ods" -> DocType.Office(ext)
        "epub" -> DocType.Epub
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "svg" -> DocType.Image
        else -> null
    }

    private fun startsWith(b: ByteArray, s: String, at: Int = 0): Boolean {
        val sb = s.toByteArray(Charsets.ISO_8859_1)
        if (b.size < at + sb.size) return false
        for (i in sb.indices) if (b[at + i] != sb[i]) return false
        return true
    }

    private fun isImage(h: ByteArray): Boolean =
        (h.size >= 4 && h[0] == 0x89.toByte() && startsWith(h, "PNG", 1)) ||
            (h.size >= 3 && h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() && h[2] == 0xFF.toByte()) ||
            startsWith(h, "GIF8") || startsWith(h, "BM") ||
            (startsWith(h, "RIFF") && startsWith(h, "WEBP", 8))

    private fun isOle2(h: ByteArray): Boolean =
        h.size >= 4 && h[0] == 0xD0.toByte() && h[1] == 0xCF.toByte() && h[2] == 0x11.toByte() && h[3] == 0xE0.toByte()

    fun isZip(h: ByteArray): Boolean =
        h.size >= 4 && h[0] == 'P'.code.toByte() && h[1] == 'K'.code.toByte() &&
            ((h[2] == 0x03.toByte() && h[3] == 0x04.toByte()) || h[2] == 0x05.toByte() || h[2] == 0x07.toByte())

    private fun ole2(head: ByteArray, ext: String): DocType {
        // OLE2 = legacy doc/ppt/xls or HWP 5. HWP writes this string early on.
        if (indexOf(head, "HWP Document File") >= 0) return DocType.Hwp
        return when (ext) {
            "hwp" -> DocType.Hwp
            "ppt" -> DocType.Office("ppt")
            "xls" -> DocType.Office("xls")
            else -> DocType.Office("doc")
        }
    }

    private fun zipType(zip: ZipInfo?, ext: String): DocType {
        if (zip != null) {
            val mt = zip.mimetype.orEmpty()
            when {
                "epub" in mt -> return DocType.Epub
                "hwp" in mt -> return DocType.Hwpx
                mt.endsWith("opendocument.text") -> return DocType.Office("odt")
                mt.endsWith("opendocument.spreadsheet") -> return DocType.Office("ods")
                mt.endsWith("opendocument.presentation") -> return DocType.Office("odp")
            }
            val names = zip.names
            if ("version.xml" in names && names.any { it.startsWith("Contents/") }) return DocType.Hwpx
            if (names.any { it.startsWith("word/") }) return DocType.Office("docx")
            if (names.any { it.startsWith("ppt/") }) return DocType.Office("pptx")
            if (names.any { it.startsWith("xl/") }) return DocType.Office("xlsx")
            if (names.any { it.startsWith("OEBPS/") || it == "META-INF/container.xml" }) return DocType.Epub
        }
        return when (ext) {
            "hwpx", "epub", "docx", "pptx", "xlsx", "odt", "ods", "odp" ->
                byExtension(ext) ?: DocType.Unknown("application/zip")
            else -> DocType.Unknown("application/zip")
        }
    }

    /**
     * Entry names and the `mimetype` entry of a ZIP, reading at most
     * [MAX_ZIP_ENTRIES] entries and [MAX_ZIP_BYTES] inflated bytes, so a zip
     * bomb costs a bounded amount of work.
     */
    fun scanZip(input: InputStream): ZipInfo {
        val names = ArrayList<String>()
        var mimetype: String? = null
        var budget = MAX_ZIP_BYTES
        val buf = ByteArray(8192)
        ZipInputStream(input.buffered()).use { zis ->
            while (names.size < MAX_ZIP_ENTRIES) {
                val e = zis.nextEntry ?: break
                names.add(e.name)
                if (e.name == "mimetype") {
                    var n = 0
                    while (n < 200) {
                        val r = zis.read(buf, n, 200 - n)
                        if (r < 0) break
                        n += r
                    }
                    mimetype = String(buf, 0, n, Charsets.US_ASCII).trim()
                }
                if (decisive(names, mimetype)) break
                // Skip the rest of the entry by reading it, counting what we inflate.
                while (true) {
                    val n = zis.read(buf)
                    if (n < 0) break
                    budget -= n
                    if (budget < 0) return ZipInfo(names, mimetype)
                }
            }
        }
        return ZipInfo(names, mimetype)
    }

    /** Stop once the entries seen so far settle the type. */
    private fun decisive(names: List<String>, mimetype: String?): Boolean {
        if (mimetype != null && ("hwp" in mimetype || "epub" in mimetype || "opendocument" in mimetype)) return true
        val last = names.last()
        if (last.startsWith("word/") || last.startsWith("ppt/") || last.startsWith("xl/")) return true
        return "version.xml" in names && names.any { it.startsWith("Contents/") }
    }

    private fun indexOf(b: ByteArray, s: String): Int {
        val sb = s.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..b.size - sb.size) {
            for (j in sb.indices) if (b[i + j] != sb[j]) continue@outer
            return i
        }
        return -1
    }

    /** No NUL bytes and almost no control characters (BOM-marked UTF-16 is text too). */
    fun looksTextual(h: ByteArray): Boolean {
        if (h.isEmpty()) return true
        if (h.size >= 2 && ((h[0] == 0xFF.toByte() && h[1] == 0xFE.toByte()) ||
                (h[0] == 0xFE.toByte() && h[1] == 0xFF.toByte()))) return true
        var control = 0
        for (byte in h) {
            val v = byte.toInt() and 0xFF
            if (v == 0) return false
            if (v < 0x09 || v in 0x0E..0x1A) control++
        }
        return control * 20 <= h.size
    }
}
