package io.github.jayyo.readeverything.core

/**
 * The detected logical type of a document, used to route to the right renderer.
 * Types with a [conversionExt] go to the conversion server and come back as PDF;
 * everything else renders on the device.
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

    /** doc, docx, ppt, pptx, xls, xlsx, odt, odp, ods */
    class Office(val ext: String) : DocType(ext.uppercase())
    object Hwp : DocType("HWP")
    object Hwpx : DocType("HWPX")

    class Unknown(val mime: String?) : DocType("Unknown")

    /** File extension to send to the server, or null when this renders on the device. */
    val conversionExt: String?
        get() = when (this) {
            is Office -> ext
            is Hwp -> "hwp"
            is Hwpx -> "hwpx"
            else -> null
        }
}
