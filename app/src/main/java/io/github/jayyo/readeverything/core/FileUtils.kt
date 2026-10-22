package io.github.jayyo.readeverything.core

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

class TooLargeException(val limit: Long) : IOException("larger than $limit bytes")

object FileUtils {

    /** Whole stream into memory, refusing anything over [limit] bytes. */
    fun readBytes(resolver: ContentResolver, uri: Uri, limit: Long): ByteArray =
        (resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")).use { readLimited(it, limit) }

    fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw TooLargeException(limit)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Read up to [n] bytes from the head of the stream. */
    fun readHead(resolver: ContentResolver, uri: Uri, n: Int): ByteArray =
        resolver.openInputStream(uri)?.use { ins ->
            val buf = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = ins.read(buf, off, n - off)
                if (r < 0) break
                off += r
            }
            if (off == n) buf else buf.copyOf(off)
        } ?: ByteArray(0)

    /** Name for display only; it never becomes a path. */
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

    /** Copy until EOF, failing past [limit] bytes; [checkActive] runs between chunks. */
    fun copyLimited(input: InputStream, out: java.io.OutputStream, limit: Long, checkActive: () -> Unit = {}) {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            checkActive()
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw TooLargeException(limit)
            out.write(buf, 0, n)
        }
    }

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /**
     * Decode bytes to text. Sniffs a BOM first, then tries strict UTF-8, then falls back to
     * MS949 (CP949/EUC-KR superset), the usual encoding of Korean files saved by Excel or Notepad.
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

    /**
     * Copy a content URI into the app cache so PdfRenderer gets a seekable file.
     * The cache file is named after the content hash: two different files with
     * the same display name never overwrite each other.
     */
    fun copyToCache(resolver: ContentResolver, uri: Uri, cacheDir: File, limit: Long,
                    checkActive: () -> Unit = {}): File {
        val dir = File(cacheDir, "imports").apply { mkdirs() }
        val tmp = File(dir, "${UUID.randomUUID()}.part")
        val md = MessageDigest.getInstance("SHA-256")
        try {
            (resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")).use { ins ->
                DigestInputStream(ins, md).use { din -> tmp.outputStream().use { copyLimited(din, it, limit, checkActive) } }
            }
            val out = File(dir, md.digest().toHex() + ".pdf")
            if (out.exists() && out.length() == tmp.length()) {
                tmp.delete()
            } else if (!tmp.renameTo(out)) {
                throw IOException("cannot move ${tmp.name}")
            }
            return out
        } finally {
            tmp.delete()
        }
    }
}
