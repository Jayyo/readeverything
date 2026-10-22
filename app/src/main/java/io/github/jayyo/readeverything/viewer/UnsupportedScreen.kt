package io.github.jayyo.readeverything.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.jayyo.readeverything.R

@Composable
fun UnsupportedScreen(label: String) {
    CenterMessage(
        if (label.isEmpty()) stringResource(R.string.unsupported_unknown)
        else stringResource(R.string.unsupported, label),
    )
}
