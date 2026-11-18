package com.arny.dnshostsgenerator.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.arny.dnshostsgenerator.domain.GenerationResult
import com.arny.dnshostsgenerator.domain.GenerationStats
import com.arny.dnshostsgenerator.domain.HostLine
import com.arny.dnshostsgenerator.presentation.theme.AppTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Диалог подтверждения обновления hosts рисуется с текстом в Column:
 * при нескольких детях прямо в слоте text строки накладывались друг на друга.
 */
@OptIn(ExperimentalTestApi::class)
class UpdateHostsDialogTest {

    private val result = GenerationResult(
        presetTitle = "dns.bezmezhau.com",
        outputFileName = "hosts.txt",
        lines = listOf(HostLine.Resolved("chatgpt.com", "31.58.216.201", listOf("31.58.216.201"))),
        stats = GenerationStats(
            lineCount = 1,
            resolvedCount = 1,
            forwardedCount = 0,
            unresolvedCount = 0,
            duplicateCount = 0,
        ),
    )

    @Test
    fun dialogShowsBothParagraphsAndSnapshot() = runComposeUiTest {
        setContent {
            AppTheme {
                Surface {
                    Box(Modifier.size(411.dp, 900.dp)) {
                        HostsGeneratorScreen(
                            presets = emptyList(),
                            selectedPresetIds = emptySet(),
                            domainText = "chatgpt.com",
                            domainGroups = emptyList(),
                            dedupEnabled = true,
                            isGenerating = false,
                            progress = null,
                            progressText = "Готово",
                            statusText = "Готово",
                            results = listOf(result),
                            selectedResultIndex = 0,
                            onPresetChecked = { _, _ -> },
                            onSelectAllPresets = {},
                            onClearPresets = {},
                            onDedupChanged = {},
                            onDomainTextChanged = {},
                            onDomainGroupEnabledChanged = { _, _ -> },
                            onSetAllDomainGroupsEnabled = {},
                            onAddDomainGroup = { _ -> },
                            onRenameDomainGroup = { _, _ -> },
                            onDeleteDomainGroup = {},
                            onAddDomains = { _, _ -> },
                            onRenameDomain = { _, _ -> },
                            onDeleteDomain = {},
                            onGenerate = {},
                            onCancelGeneration = {},
                            onSelectResult = {},
                            onCopyText = {},
                            onSaveTextFile = { _, _ -> },
                            onUpdateSystemHosts = {},
                        )
                    }
                }
            }
        }

        onNodeWithText("Обновить hosts").performClick()

        onNodeWithText("Текущий hosts будет заменён", substring = true).assertExists()
        onNodeWithText("После записи очистите кэш DNS", substring = true).assertExists()

        // С открытым диалогом корней два: экран и окно диалога.
        val image = onAllNodes(isRoot()).onLast().captureToImage().toAwtImage()
        val out = File(System.getProperty("java.io.tmpdir"), "update_hosts_dialog.png")
        ImageIO.write(image, "png", out)
        println("snapshot: ${out.absolutePath}")
    }
}
