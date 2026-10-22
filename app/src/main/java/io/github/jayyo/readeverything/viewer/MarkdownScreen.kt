package io.github.jayyo.readeverything.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

@Composable
fun MarkdownScreen(text: String, modifier: Modifier = Modifier) {
    val t = MaterialTheme.typography
    Markdown(
        content = text,
        // GFM flavour so tables, strikethrough and task lists are parsed
        flavour = GFMFlavourDescriptor(),
        // The renderer's defaults use display-size headings, too big for a phone.
        typography = markdownTypography(
            h1 = t.headlineMedium, h2 = t.titleLarge, h3 = t.titleMedium,
            h4 = t.titleSmall, h5 = t.bodyLarge, h6 = t.bodyLarge,
        ),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    )
}
