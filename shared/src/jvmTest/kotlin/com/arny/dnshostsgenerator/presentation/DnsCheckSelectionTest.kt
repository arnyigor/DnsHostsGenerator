package com.arny.dnshostsgenerator.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.arny.dnshostsgenerator.domain.DnsCheckResult
import com.arny.dnshostsgenerator.domain.DnsCheckStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Выбор серверов прямо из результатов проверки: вердикт относится к проверенному домену,
 * поэтому в генерацию переносится выбор серверов, а не готовые адреса.
 */
@OptIn(ExperimentalTestApi::class)
class DnsCheckSelectionTest {

    private fun result(id: String, status: DnsCheckStatus) = DnsCheckResult(
        presetId = id,
        title = id,
        dnsAddress = "$id.example",
        status = status,
        elapsedMs = 100,
        ip = if (status == DnsCheckStatus.Works) "1.1.1.1" else null,
    )

    private val state = DnsCheckUiState(
        domain = "gemini.google.com",
        results = listOf(
            result("works", DnsCheckStatus.Works),
            result("dead", DnsCheckStatus.Unreachable),
        ),
        selectablePresetIds = setOf("works"),
    )

    @Test
    fun onlyWorkingServersCanBeMarked() = runComposeUiTest {
        setContent {
            Surface {
                DnsCheckCard(
                    state = state,
                    onDomainChanged = {},
                    onRunCheck = {},
                    onCancelCheck = {},
                )
            }
        }
        // Блок по умолчанию свёрнут — разворачиваем.
        onNodeWithText("Проверка DNS").performClick()

        // У рабочего сервера есть чекбокс, у нерабочего — нет.
        onNodeWithContentDescription("Выбрать works").assertExists()
        onNodeWithContentDescription("Выбрать dead").assertDoesNotExist()
    }

    @Test
    fun markingServerEnablesApplyButton() = runComposeUiTest {
        var marked: Pair<String, Boolean>? = null
        var applied = 0
        setContent {
            Surface {
                DnsCheckCard(
                    state = state,
                    onDomainChanged = {},
                    onRunCheck = {},
                    onCancelCheck = {},
                    onPresetMarked = { id, checked -> marked = id to checked },
                    onUseMarked = { applied++ },
                )
            }
        }
        onNodeWithText("Проверка DNS").performClick()

        // Без отметок кнопка недоступна.
        onNodeWithText("Использовать отмеченные для генерации (0)").assertIsNotEnabled()

        onNodeWithContentDescription("Выбрать works").performClick()
        assertEquals("works" to true, marked)
        assertTrue(applied == 0, "выбор сервера не должен сразу запускать генерацию")
    }
}
