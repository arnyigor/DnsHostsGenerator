package com.arny.dnshostsgenerator.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.domain.DnsProviderPreset
import com.arny.dnshostsgenerator.presentation.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Свой DNS-провайдер: добавление через диалог и удаление из списка. */
class CustomPresetUiTest {

    private val builtIn = DnsProviderPreset(
        id = "builtin",
        title = "dns.example.org",
        primaryDns = "dns.example.org",
        outputFileName = "hosts_example.txt",
    )

    private val custom = DnsProviderPreset(
        id = "custom_1",
        title = "Мой SmartDNS",
        primaryDns = "dns.mydomain.net",
        outputFileName = "hosts_my.txt",
        isCustom = true,
    )

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.renderScreen(
        presets: List<DnsProviderPreset> = listOf(builtIn, custom),
        onAdd: (String, String, String?) -> Unit = { _, _, _ -> },
        onDelete: (String) -> Unit = {},
    ) {
        setContent {
            AppTheme {
                Surface {
                    Box(Modifier.size(411.dp, 891.dp)) {
                        HostsGeneratorScreen(
                            presets = presets,
                            selectedPresetIds = setOf("custom_1"),
                            domainText = "chatgpt.com",
                            domainGroups = emptyList(),
                            dedupEnabled = true,
                            isGenerating = false,
                            progress = null,
                            progressText = "Готово",
                            statusText = "Ожидание",
                            results = emptyList(),
                            selectedResultIndex = 0,
                            onPresetChecked = { _, _ -> },
                            onSelectAllPresets = {},
                            onClearPresets = {},
                            onDedupChanged = {},
                            onDomainTextChanged = {},
                            onDomainGroupEnabledChanged = { _, _ -> },
                            onAddDomainGroup = {},
                            onRenameDomainGroup = { _, _ -> },
                            onDeleteDomainGroup = {},
                            onAddDomains = { _, _ -> },
                            onRenameDomain = { _, _ -> },
                            onDeleteDomain = {},
                            onGenerate = {},
                            onSelectResult = {},
                            onCopyText = {},
                            onSaveTextFile = { _, _ -> },
                            onAddCustomPreset = onAdd,
                            onDeleteCustomPreset = onDelete,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun customPresetIsAddedFromDialog() = runComposeUiTest {
        var added: Triple<String, String, String?>? = null
        renderScreen(onAdd = { t, a, d -> added = Triple(t, a, d) })

        onNodeWithText("DNS-провайдер").performClick()
        onNodeWithText("Добавить свой DNS").performClick()

        onNodeWithText("Название").performTextInput("Офисный DNS")
        onNodeWithText("Адрес DNS").performTextInput("10.0.0.53")
        onNodeWithText("Добавить").performClick()

        val result = assertNotNull(added, "диалог должен передать значения наверх")
        assertEquals("Офисный DNS", result.first)
        assertEquals("10.0.0.53", result.second)
        assertEquals("", result.third)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addButtonIsDisabledWithoutTitle() = runComposeUiTest {
        renderScreen(onAdd = { _, _, _ -> })

        onNodeWithText("DNS-провайдер").performClick()
        onNodeWithText("Добавить свой DNS").performClick()
        onNodeWithText("Адрес DNS").performTextInput("10.0.0.53")

        // Без названия добавление невозможно; нажатие не должно открыть/подтвердить ничего.
        onNodeWithText("Добавить").performClick()
        onNodeWithText("Название").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun customPresetCanBeDeleted() = runComposeUiTest {
        var deletedId: String? = null
        renderScreen(onDelete = { deletedId = it })

        onNodeWithText("DNS-провайдер").performClick()
        onNodeWithText("свой • dns.mydomain.net", substring = true).assertExists()
        onNodeWithContentDescription("Удалить свой DNS").performClick()

        assertEquals("custom_1", deletedId)
    }
}
