package io.github.jayyo.readeverything.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.jayyo.readeverything.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_LINE = 4_000   // longer lines are shown in pieces

/** One LazyColumn item per line (or piece of a very long line), so only what is visible is laid out. */
@Composable
fun TextScreen(text: String, monospace: Boolean = false, modifier: Modifier = Modifier) {
    val empty = stringResource(R.string.empty_file)
    val lines by produceState<List<String>?>(null, text) {
        value = withContext(Dispatchers.Default) {
            if (text.isEmpty()) listOf(empty)
            else text.split('\n').flatMap { if (it.length <= MAX_LINE) listOf(it) else it.chunked(MAX_LINE) }
        }
    }
    val l = lines ?: return CenterLoading()
    SelectionContainer(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
            items(l) { line ->
                Text(
                    text = line.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                )
            }
        }
    }
}
