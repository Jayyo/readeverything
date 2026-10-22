package io.github.jayyo.readeverything.viewer

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.github.jayyo.readeverything.R

@Composable
fun ImageScreen(uri: Uri, modifier: Modifier = Modifier) {
    var failed by remember(uri) { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onState = { failed = it is AsyncImagePainter.State.Error },
        )
        if (failed) CenterMessage(stringResource(R.string.err_image))
    }
}
