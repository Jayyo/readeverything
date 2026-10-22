package io.github.jayyo.readeverything.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jayyo.readeverything.convert.ConversionClient
import io.github.jayyo.readeverything.convert.ServerConfig
import java.io.File

private sealed interface ConvState {
    object Loading : ConvState
    data class Ready(val file: File) : ConvState
    data class Failed(val message: String) : ConvState
}

/**
 * For office/HWP formats with no faithful on-device renderer: upload bytes to the conversion
 * server, cache the returned PDF by content hash, and display it through the shared PDF viewer.
 */
@Composable
fun ConvertThenPdfScreen(bytes: ByteArray, displayName: String, typeLabel: String) {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<ConvState>(ConvState.Loading) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(displayName, attempt) {
        state = ConvState.Loading
        state = ConversionClient.convertToPdf(ctx, bytes, displayName).fold(
            onSuccess = { ConvState.Ready(it) },
            onFailure = { ConvState.Failed(it.message ?: "알 수 없는 오류") },
        )
    }

    when (val s = state) {
        is ConvState.Loading -> CenterLoading("$typeLabel 문서를 변환하는 중…")
        is ConvState.Ready -> PdfScreen(s.file)
        is ConvState.Failed -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "$typeLabel 문서는 변환 서버가 필요합니다.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(s.message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("서버: ${ServerConfig.baseUrl}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { attempt++ }) { Text("다시 시도") }
        }
    }
}
