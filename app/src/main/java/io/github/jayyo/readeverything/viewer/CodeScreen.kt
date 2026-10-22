package io.github.jayyo.readeverything.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CodeBg = Color(0xFF1E1E1E)
private val CodeFg = Color(0xFFE6E6E6)
private const val MAX_HIGHLIGHT_CHARS = 300_000   // bigger files show as plain monospace

/** This composes the whole file as one Text, so big files go to [TextScreen] instead. */
@Composable
fun CodeScreen(text: String, ext: String, modifier: Modifier = Modifier) {
    if (text.length > MAX_HIGHLIGHT_CHARS) return TextScreen(text, monospace = true, modifier = modifier)
    val annotated by produceState(AnnotatedString(text), text, ext) {
        value = withContext(Dispatchers.Default) { highlight(text, ext) }
    }
    val v = rememberScrollState()
    val h = rememberScrollState()
    SelectionContainer {
        Box(
            modifier
                .fillMaxSize()
                .background(CodeBg)
                .verticalScroll(v)
                .horizontalScroll(h)
                .padding(12.dp),
        ) {
            Text(
                text = annotated,
                color = CodeFg,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                softWrap = false,
            )
        }
    }
}

private fun highlight(code: String, ext: String): AnnotatedString = try {
    val hl = Highlights.Builder()
        .code(code)
        .language(languageFor(ext))
        .theme(SyntaxThemes.darcula(darkMode = true))
        .build()
    val len = code.length
    buildAnnotatedString {
        append(code)
        hl.getHighlights().forEach { r ->
            when (r) {
                is ColorHighlight -> {
                    val s = r.location.start.coerceIn(0, len)
                    val e = r.location.end.coerceIn(s, len)
                    if (e > s) addStyle(SpanStyle(color = Color(0xFF000000 or (r.rgb.toLong() and 0xFFFFFF))), s, e)
                }
                is BoldHighlight -> {
                    val s = r.location.start.coerceIn(0, len)
                    val e = r.location.end.coerceIn(s, len)
                    if (e > s) addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
                }
            }
        }
    }
} catch (e: Exception) {
    AnnotatedString(code)
}

private fun languageFor(ext: String): SyntaxLanguage = when (ext) {
    "kt", "kts" -> SyntaxLanguage.KOTLIN
    "java" -> SyntaxLanguage.JAVA
    "py" -> SyntaxLanguage.PYTHON
    "js", "jsx", "mjs" -> SyntaxLanguage.JAVASCRIPT
    "ts", "tsx" -> SyntaxLanguage.TYPESCRIPT
    "go" -> SyntaxLanguage.GO
    "rs" -> SyntaxLanguage.RUST
    "c", "h" -> SyntaxLanguage.C
    "cpp", "cc", "hpp" -> SyntaxLanguage.CPP
    "cs" -> SyntaxLanguage.CSHARP
    "php" -> SyntaxLanguage.PHP
    "rb" -> SyntaxLanguage.RUBY
    "swift" -> SyntaxLanguage.SWIFT
    "dart" -> SyntaxLanguage.DART
    "sh", "bash" -> SyntaxLanguage.SHELL
    "pl" -> SyntaxLanguage.PERL
    else -> SyntaxLanguage.DEFAULT
}
