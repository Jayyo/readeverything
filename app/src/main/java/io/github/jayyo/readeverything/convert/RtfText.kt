package io.github.jayyo.readeverything.convert

import com.rtfparserkit.converter.text.StringTextConverter
import com.rtfparserkit.parser.RtfStreamSource
import java.io.ByteArrayInputStream

/** Extracts plain text from RTF bytes using rtfparserkit (on-device, no server). Throws on bad input. */
object RtfText {
    fun extract(bytes: ByteArray): String {
        val converter = StringTextConverter()
        converter.convert(RtfStreamSource(ByteArrayInputStream(bytes)))
        return converter.text
    }
}
