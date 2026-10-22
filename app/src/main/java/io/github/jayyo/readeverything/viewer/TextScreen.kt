package io.github.jayyo.readeverything.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Streams a (possibly huge) text blob line-by-line into a LazyColumn so only visible lines compose. */
@Composable
fun TextScreen(text: String, monospace: Boolean = false, modifier: Modifier = Modifier) {
    val lines = remember(text) { if (text.isEmpty()) listOf("(빈 파일)") else text.split('\n') }
    SelectionContainer(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
            items(lines) { line ->
                Text(
                    text = line.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                )
            }
        }
    }
}
