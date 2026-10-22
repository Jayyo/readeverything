package io.github.jayyo.readeverything.core

import android.content.ContentResolver
import android.net.Uri
import java.util.zip.ZipInputStream

/**
 * Detects the true document type from magic bytes (not just extension), since content:// URIs
 * often arrive as application/octet-stream. HWPX/DOCX/PPTX/XLSX/EPUB all share the ZIP magic
 * 50 4B 03 04, so we peek inside the archive to disambiguate.
 */
object TypeDetector {

    fun detect(resolver: ContentResolver, uri: Uri, name: String): DocType {
        val ext = FileUtils.extensionOf(name)
        val head = FileUtils.readHead(resolver, uri, 16)

        if (startsWith(head, "%PDF")) return DocType.Pdf
        imageMagic(head)?.let { return it }
        if (isOle2(head)) return peekOle2(resolver, uri, ext)
        if (isZip(head)) return peekZip(resolver, uri, ext)
        if (startsWith(head, "{\\rtf")) return DocType.Rtf

        return when (ext) {
            "md", "markdown" -> DocType.Markdown
            "json" -> DocType.Json
            "csv", "tsv" -> DocType.Csv
            "rtf" -> DocType.Rtf
            "pdf" -> DocType.Pdf
            "hwp" -> DocType.Hwp
            "hwpx" -> DocType.Hwpx
            "doc" -> DocType.Office("DOC")
            "docx" -> DocType.Office("DOCX")
            "ppt" -> DocType.Office("PPT")
            "pptx" -> DocType.Office("PPTX")
            "xls" -> DocType.Office("XLS")
            "xlsx" -> DocType.Office("XLSX")
            "epub" -> DocType.Epub
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "svg" -> DocType.Image
            else -> if (looksTextual(resolver, uri)) DocType.Text(ext.ifEmpty { "txt" }) else DocType.Unknown(null)
        }
    }

    private fun startsWith(b: ByteArray, s: String): Boolean {
        val sb = s.toByteArray(Charsets.ISO_8859_1)
        if (b.size < sb.size) return false
        for (i in sb.indices) if (b[i] != sb[i]) return false
        return true
    }

    private fun imageMagic(h: ByteArray): DocType? {
        if (h.size >= 4 && h[0] == 0x89.toByte() && h[1] == 'P'.code.toByte() && h[2] == 'N'.code.toByte() && h[3] == 'G'.code.toByte()) return DocType.Image
        if (h.size >= 3 && h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() && h[2] == 0xFF.toByte()) return DocType.Image
        if (h.size >= 3 && h[0] == 'G'.code.toByte() && h[1] == 'I'.code.toByte() && h[2] == 'F'.code.toByte()) return DocType.Image
        if (h.size >= 2 && h[0] == 'B'.code.toByte() && h[1] == 'M'.code.toByte()) return DocType.Image
        return null
    }

    private fun isOle2(h: ByteArray): Boolean =
        h.size >= 4 && h[0] == 0xD0.toByte() && h[1] == 0xCF.toByte() && h[2] == 0x11.toByte() && h[3] == 0xE0.toByte()

    private fun isZip(h: ByteArray): Boolean =
        h.size >= 4 && h[0] == 'P'.code.toByte() && h[1] == 'K'.code.toByte() &&
            ((h[2] == 0x03.toByte() && h[3] == 0x04.toByte()) || h[2] == 0x05.toByte() || h[2] == 0x07.toByte())

    private fun peekOle2(resolver: ContentResolver, uri: Uri, ext: String): DocType {
        // OLE2 container = legacy doc/ppt/xls OR HWP5. Look for the HWP signature in the head.
        val chunk = FileUtils.readHead(resolver, uri, 16384)
        if (containsAscii(chunk, "HWP Document File")) return DocType.Hwp
        return when (ext) {
            "hwp" -> DocType.Hwp
            "ppt" -> DocType.Office("PPT")
            "xls" -> DocType.Office("XLS")
            else -> DocType.Office("DOC")
        }
    }

    private fun peekZip(resolver: ContentResolver, uri: Uri, ext: String): DocType {
        runCatching {
            resolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(ins.buffered()).use { zis ->
                    val names = ArrayList<String>()
                    var count = 0
                    var e = zis.nextEntry
                    while (e != null && count < 80) {
                        val n = e.name
                        names.add(n)
                        if (n == "mimetype") {
                            val mt = zis.readBytes().toString(Charsets.US_ASCII).trim()
                            if (mt.contains("epub")) return DocType.Epub
                            if (mt.contains("hwp")) return DocType.Hwpx
                        }
                        zis.closeEntry()
                        e = zis.nextEntry
                        count++
                    }
                    if (names.any { it == "version.xml" } && names.any { it.startsWith("Contents/") }) return DocType.Hwpx
                    if (names.any { it.startsWith("word/") }) return DocType.Office("DOCX")
                    if (names.any { it.startsWith("ppt/") }) return DocType.Office("PPTX")
                    if (names.any { it.startsWith("xl/") }) return DocType.Office("XLSX")
                    if (names.any { it.contains("OEBPS") || (it.startsWith("META-INF/") && it.contains("container")) }) return DocType.Epub
                }
            }
        }
        return when (ext) {
            "hwpx" -> DocType.Hwpx
            "epub" -> DocType.Epub
            "docx" -> DocType.Office("DOCX")
            "pptx" -> DocType.Office("PPTX")
            "xlsx" -> DocType.Office("XLSX")
            else -> DocType.Unknown("application/zip")
        }
    }

    private fun containsAscii(b: ByteArray, s: String): Boolean {
        val sb = s.toByteArray(Charsets.US_ASCII)
        if (b.size < sb.size) return false
        outer@ for (i in 0..b.size - sb.size) {
            for (j in sb.indices) if (b[i + j] != sb[j]) continue@outer
            return true
        }
        return false
    }

    private fun looksTextual(resolver: ContentResolver, uri: Uri): Boolean {
        val h = FileUtils.readHead(resolver, uri, 1024)
        if (h.isEmpty()) return true
        var control = 0
        for (byte in h) {
            val v = byte.toInt() and 0xFF
            if (v == 0) return false
            if (v < 0x09) control++
        }
        return control < h.size / 20
    }
}
