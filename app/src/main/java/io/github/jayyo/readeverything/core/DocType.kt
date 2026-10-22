package io.github.jayyo.readeverything.core

/**
 * The detected logical type of a document, used to route to the right renderer.
 * Types whose [needsConversion] is true are sent to the conversion server and
 * shown through the shared PDF viewer; everything else renders on-device.
 */
sealed class DocType(val label: String) {
    object Pdf : DocType("PDF")
    object Image : DocType("Image")
    object Epub : DocType("EPUB")
    object Markdown : DocType("Markdown")
    object Json : DocType("JSON")
    object Csv : DocType("CSV")
    object Rtf : DocType("RTF")
    class Text(val sub: String) : DocType("Text")

    // Binary office / Hangul formats that require the LibreOffice conversion server.
    class Office(val kind: String) : DocType(kind) // DOC, DOCX, PPT, PPTX, XLS, XLSX
    object Hwp : DocType("HWP")
    object Hwpx : DocType("HWPX")

    class Unknown(val mime: String?) : DocType("Unknown")

    /** True when on-device rendering is not faithful and the server must produce a PDF. */
    val needsConversion: Boolean
        get() = this is Office || this is Hwp || this is Hwpx
}
