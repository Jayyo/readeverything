package io.github.jayyo.readeverything.viewer

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.jayyo.readeverything.R
import io.github.jayyo.readeverything.SettingsActivity
import io.github.jayyo.readeverything.convert.ConversionClient
import io.github.jayyo.readeverything.convert.Server
import io.github.jayyo.readeverything.convert.ServerSettings
import io.github.jayyo.readeverything.core.Prepared
import kotlinx.coroutines.CancellationException
import java.io.File

private sealed interface ConvState {
    object NoServer : ConvState
    data class Ask(val server: Server) : ConvState
    data class Uploading(val server: Server) : ConvState
    data class Ready(val file: File, val fromCache: Boolean) : ConvState
    data class Failed(val message: String, val server: Server) : ConvState
}

/**
 * Office and HWP files have no faithful renderer on the phone. They are sent to
 * the user's conversion server, but only after the user says so for this file;
 * a PDF converted earlier from the same bytes opens without asking.
 */
@Composable
fun ConvertThenPdfScreen(doc: Prepared.Convert) {
    val ctx = LocalContext.current
    var settingsVersion by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        settingsVersion++          // back from the settings screen: look again
        onPauseOrDispose { }
    }
    var state by remember(doc.sha) { mutableStateOf<ConvState?>(null) }

    LaunchedEffect(doc.sha, settingsVersion) {
        val cached = ConversionClient.cached(ctx, doc.sha)
        if (cached != null) {
            state = ConvState.Ready(cached, fromCache = true)
            return@LaunchedEffect
        }
        if (state is ConvState.Uploading) return@LaunchedEffect
        val server = ServerSettings.load(ctx)
        state = if (server == null) ConvState.NoServer else ConvState.Ask(server)
    }

    val s = state
    if (s is ConvState.Uploading) {
        LaunchedEffect(s) {
            state = try {
                ConvState.Ready(ConversionClient.convert(ctx, s.server, doc.bytes, doc.sha, doc.ext), false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConvState.Failed(e.message ?: e.javaClass.simpleName, s.server)
            }
        }
    }

    val openSettings = { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) }
    when (s) {
        null -> CenterLoading()
        is ConvState.Ready -> PdfScreen(s.file)
        is ConvState.NoServer -> Notice(
            title = stringResource(R.string.convert_needs_server_title, doc.label),
            body = stringResource(R.string.convert_needs_server_body),
        ) {
            Button(onClick = openSettings) { Text(stringResource(R.string.action_set_up_server)) }
        }
        is ConvState.Ask -> Notice(
            title = stringResource(R.string.convert_ask_title, doc.label),
            body = stringResource(R.string.convert_ask_body, doc.title, s.server.display) +
                if (s.server.insecure) "\n\n" + stringResource(R.string.convert_ask_insecure) else "",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = openSettings) { Text(stringResource(R.string.action_server_settings)) }
                Button(onClick = { state = ConvState.Uploading(s.server) }) {
                    Text(stringResource(R.string.action_upload_and_open))
                }
            }
        }
        is ConvState.Uploading -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.convert_uploading, doc.label, s.server.display), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { state = ConvState.Ask(s.server) }) { Text(stringResource(R.string.action_cancel)) }
        }
        is ConvState.Failed -> Notice(
            title = stringResource(R.string.convert_failed_title, doc.label),
            body = s.message,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = openSettings) { Text(stringResource(R.string.action_server_settings)) }
                Button(onClick = { state = ConvState.Uploading(s.server) }) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun Notice(title: String, body: String, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        actions()
        Spacer(Modifier.width(0.dp))
    }
}
