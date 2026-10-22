package io.github.jayyo.readeverything.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

@Composable
fun MarkdownScreen(text: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Markdown(
        content = text,
        // GFM flavour so tables, strikethrough and task-lists render properly
        flavour = GFMFlavourDescriptor(),
        modifier = modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
    )
}
