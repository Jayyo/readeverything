package io.github.jayyo.readeverything

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jayyo.readeverything.convert.ConversionClient
import io.github.jayyo.readeverything.convert.Server
import io.github.jayyo.readeverything.convert.ServerSettings
import io.github.jayyo.readeverything.convert.ServerUrl
import io.github.jayyo.readeverything.ui.theme.ReadeverythingTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ReadeverythingTheme { SettingsScreen(onBack = ::finish) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val saved = remember { ServerSettings.load(ctx) }
    var url by rememberSaveable { mutableStateOf(saved?.display.orEmpty()) }
    var token by rememberSaveable { mutableStateOf(saved?.token.orEmpty()) }
    var allowHttp by rememberSaveable { mutableStateOf(saved?.allowHttp ?: false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var edits by remember { mutableIntStateOf(0) }   // a test result only counts for the input it tested
    val scope = rememberCoroutineScope()

    fun current(): Server? = when (val p = ServerUrl.parse(url)) {
        is ServerUrl.Parsed.Ok -> if (ConversionClient.validToken(token.trim())) {
            Server(p.url, token.trim(), allowHttp)
        } else {
            status = ctx.getString(R.string.token_invalid)
            null
        }
        is ServerUrl.Parsed.Bad -> {
            status = ctx.getString(
                when (p.problem) {
                    ServerUrl.Problem.EMPTY -> R.string.url_empty
                    ServerUrl.Problem.SCHEME -> R.string.url_scheme
                    ServerUrl.Problem.CREDENTIALS -> R.string.url_credentials
                    ServerUrl.Problem.QUERY -> R.string.url_query
                    ServerUrl.Problem.INVALID -> R.string.url_invalid
                },
            )
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = url, onValueChange = { url = it; status = null; edits++ },
                label = { Text(stringResource(R.string.settings_url)) },
                placeholder = { Text("https://convert.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it; status = null; edits++ },
                label = { Text(stringResource(R.string.settings_token)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_allow_http), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.settings_allow_http_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = allowHttp, onCheckedChange = { allowHttp = it; status = null; edits++ })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    val s = current() ?: return@OutlinedButton
                    busy = true
                    status = ctx.getString(R.string.settings_testing)
                    val tested = edits
                    scope.launch {
                        try {
                            val problem = ConversionClient.check(ctx, s)
                            if (tested == edits) status = problem ?: ctx.getString(R.string.settings_test_ok)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.action_test)) }
                Button(enabled = !busy, onClick = {
                    val s = current() ?: return@Button
                    ServerSettings.save(ctx, s)
                    onBack()
                }) { Text(stringResource(R.string.action_save)) }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (saved != null) {
                TextButton(onClick = { ServerSettings.clear(ctx); onBack() }) {
                    Text(stringResource(R.string.action_remove_server))
                }
            }
        }
    }
}
