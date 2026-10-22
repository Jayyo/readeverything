package io.github.jayyo.readeverything

import io.github.jayyo.readeverything.core.DocType
import io.github.jayyo.readeverything.core.TypeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TypeDetectorTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun detectZip(bytes: ByteArray, ext: String = ""): DocType =
        TypeDetector.detect(bytes.copyOf(16), ext, TypeDetector.scanZip(ByteArrayInputStream(bytes)))

    private fun label(t: DocType) = t.label + (t.conversionExt?.let { "/$it" } ?: "")

    @Test fun pdfAndImagesByMagic() {
        assertEquals("PDF", label(TypeDetector.detect("%PDF-1.7".toByteArray(), "", null)))
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        assertEquals("Image", label(TypeDetector.detect(png, "bin", null)))
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)
        assertEquals("Image", label(TypeDetector.detect(webp, "", null)))
    }

    @Test fun zipContainersAreToldApart() {
        assertEquals("DOCX/docx", label(detectZip(zip("[Content_Types].xml" to ByteArray(10), "word/document.xml" to ByteArray(10)))))
        assertEquals("PPTX/pptx", label(detectZip(zip("ppt/presentation.xml" to ByteArray(1)))))
        assertEquals("XLSX/xlsx", label(detectZip(zip("xl/workbook.xml" to ByteArray(1)))))
        assertEquals("HWPX/hwpx", label(detectZip(zip("mimetype" to "application/hwp+zip".toByteArray()))))
        assertEquals("HWPX/hwpx", label(detectZip(zip("version.xml" to ByteArray(1), "Contents/section0.xml" to ByteArray(1)))))
        assertEquals("EPUB", label(detectZip(zip("mimetype" to "application/epub+zip".toByteArray()))))
        assertEquals("ODT/odt", label(detectZip(zip("mimetype" to "application/vnd.oasis.opendocument.text".toByteArray()))))
        // a plain zip named .docx still goes by its name
        assertEquals("DOCX/docx", label(detectZip(zip("a.txt" to ByteArray(1)), "docx")))
        assertTrue(detectZip(zip("a.txt" to ByteArray(1))) is DocType.Unknown)
    }

    @Test fun zipScanStopsAtItsBudget() {
        // 20 MB of zeros compresses to almost nothing; the scan must not read it all.
        val big = zip("blob.bin" to ByteArray(20 * 1024 * 1024), "word/document.xml" to ByteArray(1))
        val info = TypeDetector.scanZip(ByteArrayInputStream(big))
        assertEquals(listOf("blob.bin"), info.names)
    }

    @Test fun ole2IsHwpOrLegacyOffice() {
        val ole = ByteArray(4096).also {
            byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte()).copyInto(it)
        }
        assertEquals("DOC/doc", label(TypeDetector.detect(ole, "", null)))
        assertEquals("XLS/xls", label(TypeDetector.detect(ole, "xls", null)))
        "HWP Document File".toByteArray().copyInto(ole, 1024)
        assertEquals("HWP/hwp", label(TypeDetector.detect(ole, "doc", null)))
    }

    @Test fun textByContent() {
        assertTrue(TypeDetector.detect("hello\nworld".toByteArray(), "", null) is DocType.Text)
        assertTrue(TypeDetector.detect("hi".toByteArray(), "conf", null) is DocType.Text)
        assertTrue(TypeDetector.detect(byteArrayOf(1, 2, 3, 0, 5), "", null) is DocType.Unknown)
        assertTrue(TypeDetector.detect("{\\rtf1 x}".toByteArray(), "", null) is DocType.Rtf)
        assertTrue(TypeDetector.detect("# Title".toByteArray(), "md", null) is DocType.Markdown)
    }

    @Test fun looksTextualOnShortAndUtf16Input() {
        assertTrue(TypeDetector.looksTextual("ok".toByteArray()))
        assertFalse(TypeDetector.looksTextual(byteArrayOf('a'.code.toByte(), 1)))
        assertTrue(TypeDetector.looksTextual(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'a'.code.toByte(), 0)))
    }

    @Test fun hwpxWithContentsBeforeVersion() {
        assertEquals("HWPX/hwpx", label(detectZip(zip("Contents/section0.xml" to ByteArray(1), "version.xml" to ByteArray(1)))))
    }
}
