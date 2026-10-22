package io.github.jayyo.readeverything.core

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

object FileUtils {

    fun readBytes(resolver: ContentResolver, uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

    /** Read up to [n] bytes from the head of the stream. */
    fun readHead(resolver: ContentResolver, uri: Uri, n: Int): ByteArray =
        resolver.openInputStream(uri)?.use { ins ->
            val buf = ByteArray(n)
            val read = ins.readFully(buf)
            if (read == n) buf else buf.copyOf(read)
        } ?: ByteArray(0)

    private fun InputStream.readFully(buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }

    fun displayName(resolver: ContentResolver, uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "file"
        var name: String? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        }
        return name ?: uri.lastPathSegment ?: "document"
    }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Decode bytes to text. Sniffs BOM first, then tries strict UTF-8, then falls back to
     * MS949 (CP949/EUC-KR superset) — the #1 Korean text-encoding case from Excel/Notepad exports.
     */
    fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        runCatching {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return dec.decode(ByteBuffer.wrap(bytes)).toString()
        }
        return runCatching { String(bytes, charset("MS949")) }
            .getOrElse { String(bytes, Charsets.ISO_8859_1) }
    }

    /** Copy a content uri into the app cache so it can be opened as a seekable File (PdfRenderer). */
    fun copyToCache(resolver: ContentResolver, uri: Uri, cacheDir: File, fileName: String): File {
        val dir = File(cacheDir, "imports").apply { mkdirs() }
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "doc" }
        val out = File(dir, safe)
        resolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
        return out
    }
}
