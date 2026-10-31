package com.arny.dnshostsgenerator.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.runComposeUiTest
import com.arny.dnshostsgenerator.domain.AppProfile
import com.arny.dnshostsgenerator.domain.DnsCheckResult
import com.arny.dnshostsgenerator.domain.DnsCheckStatus
import com.arny.dnshostsgenerator.domain.ProfileCheckResult
import com.arny.dnshostsgenerator.domain.ProfileHostCheck
import com.arny.dnshostsgenerator.resolver.HttpProbeResult
import com.arny.dnshostsgenerator.resolver.HttpVerdict
import com.arny.dnshostsgenerator.resolver.IpCheckResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Проверка вёрстки без устройства и без экрана: так UI можно проверять автономно,
 * даже когда телефон и компьютер заблокированы.
 */
@OptIn(ExperimentalTestApi::class)
class AppProfileCardTest {

    private val profile = AppProfileHolder.profile

    @Test
    fun showsProfileAndStartsCheck() = runComposeUiTest {
        var clicks = 0
        setContent {
            AppProfileCard(state = AppProfileUiState(), onRun = { clicks++ })
        }

        onNodeWithText("Правила для ${profile.title}").assertExists()
        onNodeWithText("Подобрать адрес").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun showsHostVerdictsAndEnablesCopyWhenAddressFound() = runComposeUiTest {
        val checks = profile.hosts.mapIndexed { index, host ->
            ProfileHostCheck(
                host = host,
                tls = IpCheckResult.SERVES,
                http = HttpProbeResult(
                    verdict = if (index == 1) HttpVerdict.CLOUDFLARE_BLOCKED else HttpVerdict.OK,
                ),
            )
        }
        val result = ProfileCheckResult(
            profileId = profile.id,
            profileTitle = profile.title,
            presetId = "p",
            presetTitle = "preset",
            ip = "45.13.237.213",
            hostChecks = checks,
            candidatesTried = listOf("45.13.237.213"),
            elapsedMs = 1_500,
        )

        setContent {
            AppProfileCard(
                state = AppProfileUiState(
                    result = result,
                    progressText = "Адрес 45.13.237.213 подходит",
                ),
                onRun = {},
            )
        }

        onNodeWithText("${profile.hosts[0]} — OK").assertExists()
        onNodeWithText("${profile.hosts[1]} — CLOUDFLARE_BLOCKED").assertExists()
        onNodeWithContentDescription("Скопировать правила").assertIsEnabled()
        onNodeWithContentDescription("Поделиться правилами").assertIsEnabled()
        onNodeWithText("Подобрать заново").assertExists()
    }

    @Test
    fun copyIsDisabledWithoutAddress() = runComposeUiTest {
        val failed = ProfileCheckResult(
            profileId = profile.id,
            profileTitle = profile.title,
            presetId = "p",
            presetTitle = "preset",
            ip = null,
            hostChecks = emptyList(),
            candidatesTried = listOf("1.1.1.1"),
            elapsedMs = 900,
            detail = "ни один адрес не подошёл",
        )

        setContent {
            AppProfileCard(state = AppProfileUiState(result = failed), onRun = {})
        }

        onNodeWithContentDescription("Скопировать правила").assertIsNotEnabled()
        onNodeWithContentDescription("Поделиться правилами").assertIsNotEnabled()
    }

    @Test
    fun dnsCheckCardHidesBodyWhenCollapsed() = runComposeUiTest {
        val state = DnsCheckUiState(
            domain = "gemini.google.com",
            results = listOf(
                DnsCheckResult(
                    presetId = "p",
                    title = "preset",
                    dnsAddress = "dns.example",
                    status = DnsCheckStatus.Works,
                    elapsedMs = 120,
                    ip = "1.1.1.1",
                ),
            ),
        )
        setContent {
            DnsCheckCard(
                state = state,
                onDomainChanged = {},
                onRunCheck = {},
                onCancelCheck = {},
            )
        }

        onNodeWithText("Домен для проверки").assertExists()
        onNodeWithText("Проверка DNS").performClick()

        onNodeWithText("Домен для проверки").assertDoesNotExist()
        // Сводка остаётся видимой в свёрнутом виде.
        val summary = onAllNodesWithText("работает 1 из 1", substring = true).fetchSemanticsNodes()
        assertTrue(summary.isNotEmpty(), "в свёрнутом виде должна остаться короткая сводка")
    }
}
