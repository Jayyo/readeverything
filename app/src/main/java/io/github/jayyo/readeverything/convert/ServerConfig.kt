package io.github.jayyo.readeverything.convert

/**
 * Conversion server endpoint. Office and HWP files are uploaded here and come back as PDF.
 * Empty means no server is configured.
 */
object ServerConfig {
    @Volatile
    var baseUrl: String = ""

    const val CONVERT_PATH = "/convert"
}
