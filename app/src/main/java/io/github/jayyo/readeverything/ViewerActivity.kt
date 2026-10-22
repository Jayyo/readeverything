package io.github.jayyo.readeverything

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.jayyo.readeverything.core.DocumentHost
import io.github.jayyo.readeverything.ui.theme.ReadeverythingTheme
import io.github.jayyo.readeverything.viewer.CenterMessage

/** "Open with" entry point: type-detect the URI and route it to a renderer. */
class ViewerActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
            ?.takeIf { it.scheme == "content" || it.scheme == "file" }
        setContent {
            ReadeverythingTheme {
                var title by remember { mutableStateOf("") }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                                }
                            },
                        )
                    },
                ) { pad ->
                    Box(Modifier.fillMaxSize().padding(pad)) {
                        if (uri == null) CenterMessage(stringResource(R.string.err_no_file))
                        else DocumentHost(uri, onTitle = { title = it })
                    }
                }
            }
        }
    }
}
