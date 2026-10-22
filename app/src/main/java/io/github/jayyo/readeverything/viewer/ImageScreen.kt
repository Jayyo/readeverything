package io.github.jayyo.readeverything.viewer

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
fun ImageScreen(uri: Uri, modifier: Modifier = Modifier) {
    AsyncImage(
        model = uri,
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
    )
}
