package io.github.jayyo.readeverything.convert

import com.rtfparserkit.converter.text.StringTextConverter
import com.rtfparserkit.parser.RtfStreamSource
import java.io.ByteArrayInputStream

/** Extracts plain text from RTF bytes using rtfparserkit (on-device, no server). */
object RtfText {
    fun extract(bytes: ByteArray): String =
        runCatching {
            val converter = StringTextConverter()
            converter.convert(RtfStreamSource(ByteArrayInputStream(bytes)))
            converter.text
        }.getOrElse { "RTF 파싱 실패: ${it.message}" }
}
