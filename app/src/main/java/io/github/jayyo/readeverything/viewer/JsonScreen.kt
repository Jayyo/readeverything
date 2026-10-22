package io.github.jayyo.readeverything.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sebastianneubauer.jsontree.JsonTree
import com.sebastianneubauer.jsontree.TreeState

/** Collapsible JSON tree. The caller guarantees [text] is valid JSON (else routed to text view). */
@Composable
fun JsonScreen(text: String, modifier: Modifier = Modifier) {
    JsonTree(
        json = text,
        onLoading = { CenterLoading() },
        // Small documents open fully expanded; big ones only show the top level.
        initialState = if (text.length < 20_000) TreeState.EXPANDED else TreeState.FIRST_ITEM_EXPANDED,
        modifier = modifier.fillMaxSize().padding(8.dp),
    )
}
