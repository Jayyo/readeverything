package io.github.jayyo.readeverything.convert

import android.content.Context
import io.github.jayyo.readeverything.core.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends office/HWP bytes to the conversion server and caches the returned PDF under
 * cacheDir/converted/<sha256>.pdf so re-opening the same file is instant and offline.
 */
object ConversionClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun cachedPdf(ctx: Context, sha: String): File =
        File(File(ctx.cacheDir, "converted").apply { mkdirs() }, "$sha.pdf")

    suspend fun convertToPdf(ctx: Context, bytes: ByteArray, displayName: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val sha = FileUtils.sha256(bytes)
                val out = cachedPdf(ctx, sha)
                if (out.exists() && out.length() > 0) return@withContext Result.success(out)

                // Field "file"; the filename extension selects the LibreOffice/H2Orestart
                // import filter on the server, so keep displayName (with extension) intact.
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        displayName,
                        bytes.toRequestBody("application/octet-stream".toMediaType()),
                    )
                    .build()
                val req = Request.Builder()
                    .url(ServerConfig.baseUrl + ServerConfig.CONVERT_PATH)
                    .post(body)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IOException("서버 변환 실패 (HTTP ${resp.code})"))
                    }
                    val part = File(out.parentFile, "$sha.part")
                    val stream = resp.body?.byteStream()
                        ?: return@withContext Result.failure(IOException("빈 응답"))
                    stream.use { ins -> part.outputStream().use { ins.copyTo(it) } }
                    if (part.length() == 0L) {
                        part.delete()
                        return@withContext Result.failure(IOException("변환 결과가 비어 있음"))
                    }
                    part.renameTo(out)
                    Result.success(out)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
