package io.github.jayyo.readeverything.convert

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A configured conversion server. */
data class Server(val base: HttpUrl, val token: String, val allowHttp: Boolean) {
    /** What the user sees: scheme, host, port and path, never the token. */
    val display: String get() = base.toString().removeSuffix("/")
    val insecure: Boolean get() = !base.isHttps
}

object ServerUrl {
    enum class Problem { EMPTY, INVALID, SCHEME, CREDENTIALS, QUERY }

    sealed interface Parsed {
        data class Ok(val url: HttpUrl) : Parsed
        data class Bad(val problem: Problem) : Parsed
    }

    /**
     * Accepts http(s)://host[:port][/prefix]. Tokens go in their own field: user
     * info, query strings and fragments are refused so nothing secret ends up in
     * a URL that may be logged or shown.
     */
    fun parse(input: String): Parsed {
        val text = input.trim()
        if (text.isEmpty()) return Parsed.Bad(Problem.EMPTY)
        val lower = text.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return Parsed.Bad(if ("://" in text) Problem.SCHEME else Problem.INVALID)
        }
        val url = text.toHttpUrlOrNull() ?: return Parsed.Bad(Problem.INVALID)
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return Parsed.Bad(Problem.CREDENTIALS)
        if (url.query != null || url.fragment != null) return Parsed.Bad(Problem.QUERY)
        // Normalise to a trailing slash so path segments append under the prefix.
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val base = url.newBuilder().encodedPath("/").apply { segments.forEach { addPathSegment(it) } }
            .build()
        return Parsed.Ok(base.newBuilder().addPathSegment("").build())
    }
}

/** Stored in plain app-private preferences; the app has backups turned off. */
object ServerSettings {
    private const val PREFS = "server"
    private const val URL = "url"
    private const val TOKEN = "token"
    private const val ALLOW_HTTP = "allow_http"

    fun load(ctx: Context): Server? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = p.getString(URL, null) ?: return null
        val url = (ServerUrl.parse(raw) as? ServerUrl.Parsed.Ok)?.url ?: return null
        return Server(url, p.getString(TOKEN, "").orEmpty(), p.getBoolean(ALLOW_HTTP, false))
    }

    fun save(ctx: Context, server: Server) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(URL, server.base.toString())
            .putString(TOKEN, server.token)
            .putBoolean(ALLOW_HTTP, server.allowHttp)
            .apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
