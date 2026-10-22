package io.github.jayyo.readeverything.convert

import android.content.Context
import io.github.jayyo.readeverything.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A failure the user should read; [message] is already localized. */
class ConversionException(message: String) : IOException(message)

/**
 * Uploads office/HWP bytes to the conversion server and caches the PDF it returns
 * under cacheDir/converted/<sha256>.pdf, so opening the same file again needs
 * no upload and works offline (until Android clears the cache).
 */
object ConversionClient {
    private const val MAX_PDF_BYTES = 200L * 1024 * 1024

    // The server may hold a request up to its queue wait plus its conversion
    // timeout (2 x 120 s by default) before the PDF starts to arrive.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        // The document and the token must not follow a redirect somewhere else.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun cachedPdf(ctx: Context, sha: String): File =
        File(File(ctx.cacheDir, "converted").apply { mkdirs() }, "$sha.pdf")

    fun cached(ctx: Context, sha: String): File? = cachedPdf(ctx, sha).takeIf { it.isFile && it.length() > 0 }

    suspend fun convert(ctx: Context, server: Server, bytes: ByteArray, sha: String, ext: String): File {
        checkScheme(ctx, server)
        val out = cachedPdf(ctx, sha)
        // The name is ours; only the extension matters to the server's import filter.
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "document.$ext",
                bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val request = authorized(server, Request.Builder().url(server.base.resolve("convert")!!).post(body))
        return execute(request) { resp ->
            if (!resp.isSuccessful) throw ConversionException(httpError(ctx, resp.code))
            val part = File(out.parentFile, "$sha.${UUID.randomUUID()}.part")
            try {
                val src = resp.body ?: throw ConversionException(ctx.getString(R.string.err_empty_response))
                src.byteStream().use { ins ->
                    part.outputStream().use { io.github.jayyo.readeverything.core.FileUtils.copyLimited(ins, it, MAX_PDF_BYTES) }
                }
                val magic = part.inputStream().use { ByteArray(5).also { b -> it.read(b) } }
                if (!String(magic, Charsets.ISO_8859_1).startsWith("%PDF-")) {
                    throw ConversionException(ctx.getString(R.string.err_not_pdf))
                }
                if (!part.renameTo(out)) throw IOException("cannot store the converted file")
                out
            } finally {
                part.delete()
            }
        }
    }

    /**
     * Null when /health answers with our token and says LibreOffice is there;
     * otherwise a message for the user. This does not prove that HWP works.
     */
    suspend fun check(ctx: Context, server: Server): String? = try {
        checkScheme(ctx, server)
        val request = authorized(server, Request.Builder().url(server.base.resolve("health")!!).get())
        execute(request) { resp ->
            if (!resp.isSuccessful) return@execute httpError(ctx, resp.code)
            val ok = runCatching { org.json.JSONObject(resp.body!!.string()).getBoolean("ok") }.getOrDefault(false)
            if (ok) null else ctx.getString(R.string.err_not_a_server)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: ConversionException) {
        e.message
    } catch (e: IOException) {
        ctx.getString(R.string.err_network, e.javaClass.simpleName)
    } catch (e: Exception) {
        ctx.getString(R.string.err_network, e.javaClass.simpleName)
    }

    /** Tokens go into an HTTP header: printable ASCII without spaces only. */
    fun validToken(token: String): Boolean = token.all { it in '!'..'~' }

    private fun checkScheme(ctx: Context, server: Server) {
        if (server.insecure && !server.allowHttp) {
            throw ConversionException(ctx.getString(R.string.err_http_not_allowed))
        }
        if (!validToken(server.token)) throw ConversionException(ctx.getString(R.string.token_invalid))
    }

    private fun authorized(server: Server, b: Request.Builder): Request {
        if (server.token.isNotEmpty()) b.header("Authorization", "Bearer ${server.token}")
        return b.build()
    }

    private fun httpError(ctx: Context, code: Int): String = when (code) {
        401, 403 -> ctx.getString(R.string.err_http_auth)
        413 -> ctx.getString(R.string.err_http_too_large)
        415 -> ctx.getString(R.string.err_http_type)
        422 -> ctx.getString(R.string.err_http_failed)
        503 -> ctx.getString(R.string.err_http_busy)
        504 -> ctx.getString(R.string.err_http_timeout)
        in 300..399 -> ctx.getString(R.string.err_http_redirect)
        else -> ctx.getString(R.string.err_http_other, code)
    }

    /** Runs the call off the main thread; cancelling the coroutine cancels the HTTP call. */
    private suspend fun <T> execute(request: Request, handle: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val call = client.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!cont.isCancelled) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching { response.use(handle) }
                        if (!cont.isCancelled) {
                            result.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
                        }
                    }
                })
            }
        }
}
