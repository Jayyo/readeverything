package io.github.jayyo.readeverything

import io.github.jayyo.readeverything.convert.ServerUrl
import io.github.jayyo.readeverything.core.FileUtils
import io.github.jayyo.readeverything.core.TooLargeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TextAndUrlTest {
    @Test fun decodesUtf8Cp949AndBoms() {
        assertEquals("한글", FileUtils.decodeText("한글".toByteArray(Charsets.UTF_8)))
        assertEquals("한글", FileUtils.decodeText("한글".toByteArray(charset("MS949"))))
        assertEquals("a", FileUtils.decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 'a'.code.toByte())))
        assertEquals("a", FileUtils.decodeText(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'a'.code.toByte(), 0)))
    }

    @Test(expected = TooLargeException::class)
    fun readLimitedRefusesBigInput() {
        FileUtils.readLimited(ByteArrayInputStream(ByteArray(2000)), 1000)
    }

    private fun ok(input: String) = (ServerUrl.parse(input) as ServerUrl.Parsed.Ok).url.toString()
    private fun bad(input: String) = (ServerUrl.parse(input) as ServerUrl.Parsed.Bad).problem

    @Test fun serverUrls() {
        assertEquals("https://convert.example.com/", ok(" https://convert.example.com "))
        assertEquals("http://192.168.0.10:3000/", ok("http://192.168.0.10:3000"))
        assertEquals("https://example.com/tools/convert/", ok("https://example.com/tools/convert"))
        assertEquals(ServerUrl.Problem.EMPTY, bad(""))
        assertEquals(ServerUrl.Problem.INVALID, bad("convert.example.com"))
        assertEquals(ServerUrl.Problem.SCHEME, bad("ftp://example.com"))
        assertEquals(ServerUrl.Problem.CREDENTIALS, bad("https://user:pw@example.com"))
        assertEquals(ServerUrl.Problem.QUERY, bad("https://example.com/?token=x"))
        assertEquals(ServerUrl.Problem.QUERY, bad("https://example.com/#x"))
    }

    @Test fun endpointsResolveUnderThePrefix() {
        val base = (ServerUrl.parse("https://example.com/tools/convert") as ServerUrl.Parsed.Ok).url
        assertEquals("https://example.com/tools/convert/convert", base.resolve("convert").toString())
        val root = (ServerUrl.parse("https://example.com") as ServerUrl.Parsed.Ok).url
        assertTrue(root.resolve("health").toString() == "https://example.com/health")
    }

    @Test fun tokensMustFitInAHeader() {
        assertTrue(io.github.jayyo.readeverything.convert.ConversionClient.validToken("abc-DEF_123+/="))
        assertTrue(!io.github.jayyo.readeverything.convert.ConversionClient.validToken("한글"))
        assertTrue(!io.github.jayyo.readeverything.convert.ConversionClient.validToken("a b"))
    }
}
