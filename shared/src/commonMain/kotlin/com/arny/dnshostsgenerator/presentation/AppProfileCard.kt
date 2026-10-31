package com.arny.dnshostsgenerator.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.domain.AppProfile
import com.arny.dnshostsgenerator.domain.BuiltInAppProfiles
import com.arny.dnshostsgenerator.domain.ProfileCheckResult
import com.arny.dnshostsgenerator.domain.buildPersonalDnsFilterRules
import kotlinx.coroutines.launch

/** Профиль приложения, для которого подбирается адрес (пока один — ChatGPT). */
object AppProfileHolder {
    val profile: AppProfile = BuiltInAppProfiles.chatGptAndroid
}

data class AppProfileUiState(
    val isRunning: Boolean = false,
    val result: ProfileCheckResult? = null,
    val progressText: String = "Один адрес для всех доменов приложения",
)

/**
 * Минимальный блок: подобрать адрес под профиль приложения и отдать готовые правила
 * для personalDNSfilter. Применяет правила отдельное приложение — мы только готовим текст.
 */
@Composable
fun AppProfileCard(
    state: AppProfileUiState,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val rules = state.result?.ip?.let { buildPersonalDnsFilterRules(AppProfileHolder.profile, it) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Rule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Правила для ${AppProfileHolder.profile.title}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${AppProfileHolder.profile.hosts.size} доменов · подбирается один адрес на все",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        rules?.let {
                            clipboardManager.setText(AnnotatedString(it))
                            scope.launch { snackbarHostState?.showSnackbar("Правила скопированы") }
                        }
                    },
                    enabled = rules != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Скопировать правила",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onRun, enabled = !state.isRunning) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.result == null) "Подобрать адрес" else "Подобрать заново")
                }
                Text(
                    state.progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.isRunning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            state.result?.hostChecks?.takeIf { it.isNotEmpty() }?.let { checks ->
                Column {
                    checks.forEach { check ->
                        Text(
                            "${check.host} — ${check.http.verdict}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
