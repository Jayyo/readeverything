package io.github.jayyo.readeverything

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.jayyo.readeverything.core.DocumentHost
import io.github.jayyo.readeverything.ui.theme.ReadeverythingTheme
import io.github.jayyo.readeverything.viewer.CenterMessage

/** Universal entry point: receives an ACTION_VIEW uri and routes it to the right renderer. */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        setContent {
            ReadeverythingTheme {
                Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                    if (uri == null) CenterMessage("열 파일이 없습니다") else DocumentHost(uri)
                }
            }
        }
    }
}
