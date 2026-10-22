package io.github.jayyo.readeverything.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jayyo.readeverything.R
import io.github.jayyo.readeverything.core.Csv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CsvScreen(text: String, tab: Boolean = false, modifier: Modifier = Modifier) {
    val table by produceState<Csv.Table?>(null, text, tab) {
        value = withContext(Dispatchers.Default) { Csv.parse(text, if (tab) '\t' else ',') }
    }
    val t = table ?: return CenterLoading()
    val cols = remember(t) { t.rows.maxOfOrNull { it.size } ?: 0 }
    val hScroll = rememberScrollState()
    var opened by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier.fillMaxSize()) {
        items(count = t.rows.size) { r ->
            val row = t.rows[r]
            Row(Modifier.horizontalScroll(hScroll).padding(vertical = 4.dp, horizontal = 8.dp)) {
                for (c in 0 until cols) {
                    val cell = row.getOrElse(c) { "" }
                    Text(
                        text = cell,
                        modifier = Modifier.width(150.dp).padding(horizontal = 6.dp)
                            .clickable(enabled = cell.isNotEmpty()) { opened = cell },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (r == 0) HorizontalDivider()
        }
        if (t.truncated) {
            item {
                Text(stringResource(R.string.csv_truncated, t.rows.size),
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    opened?.let { cell ->
        AlertDialog(
            onDismissRequest = { opened = null },
            confirmButton = { TextButton(onClick = { opened = null }) { Text(stringResource(R.string.action_close)) } },
            text = { SelectionContainer { Text(cell) } },
        )
    }
}
