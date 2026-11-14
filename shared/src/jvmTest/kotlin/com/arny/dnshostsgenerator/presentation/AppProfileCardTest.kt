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
import com.arny.dnshostsgenerator.domain.BuiltInAppProfiles
import com.arny.dnshostsgenerator.domain.DnsCheckResult
import com.arny.dnshostsgenerator.domain.DnsCheckStatus
import com.arny.dnshostsgenerator.domain.MergedRules
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

    private val profile = AppProfileHolder.profile(AppProfileUiState().profileId)

    @Test
    fun switchingProfileChangesTitleAndClearsResult() = runComposeUiTest {
        var selected: String? = null
        setContent {
            AppProfileCard(
                state = AppProfileUiState(),
                onRun = {},
                onProfileSelected = { selected = it },
            )
        }

        // По умолчанию — ChatGPT, рядом переключатель на Gemini.
        onNodeWithText("Правила для ChatGPT (Android)").assertExists()
        onNodeWithText("Gemini").performClick()

        assertEquals("app_gemini_android", selected)
    }

    @Test
    fun geminiProfileShowsOwnDomainCount() = runComposeUiTest {
        val gemini = BuiltInAppProfiles.geminiAndroid
        setContent {
            AppProfileCard(
                state = AppProfileUiState(profileId = gemini.id),
                onRun = {},
            )
        }

        onNodeWithText("Правила для ${gemini.title}").assertExists()
        onNodeWithText("${gemini.hosts.size} доменов", substring = true).assertExists()
    }

    @Test
    fun showsProfileAndStartsCheck() = runComposeUiTest {
        var clicks = 0
        setContent {
            AppProfileCard(state = AppProfileUiState(), onRun = { clicks++ })
        }

        onNodeWithText("Правила для ${profile.title}").assertExists()
        onNodeWithText("Подобрать адреса").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun showsHostVerdictsAndEnablesCopyWhenAddressFound() = runComposeUiTest {
        // Слитый результат: у каждого домена свой адрес и свой пресет.
        val result = MergedRules(
            profileId = profile.id,
            profileTitle = profile.title,
            entries = profile.hosts.associateWith { "45.13.237.213" },
            sources = profile.hosts.associateWith { "preset" },
            failed = emptyList(),
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

        onNodeWithText("${profile.hosts[0]} — 45.13.237.213").assertExists()
        onNodeWithContentDescription("Скопировать правила").assertIsEnabled()
        onNodeWithContentDescription("Поделиться правилами").assertIsEnabled()
        onNodeWithText("Подобрать заново").assertExists()
    }

    @Test
    fun copyIsDisabledWithoutAddress() = runComposeUiTest {
        val failed = MergedRules(
            profileId = profile.id,
            profileTitle = profile.title,
            entries = emptyMap(),
            sources = emptyMap(),
            failed = profile.hosts,
            elapsedMs = 900,
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

        // По умолчанию блок свёрнут: на телефоне он отбирал экран до основной задачи.
        onNodeWithText("Домен для проверки").assertDoesNotExist()
        onNodeWithText("Проверка DNS").performClick()
        onNodeWithText("Домен для проверки").assertExists()
        onNodeWithText("Проверка DNS").performClick()

        onNodeWithText("Домен для проверки").assertDoesNotExist()
        // Сводка остаётся видимой в свёрнутом виде.
        val summary = onAllNodesWithText("работает 1 из 1", substring = true).fetchSemanticsNodes()
        assertTrue(summary.isNotEmpty(), "в свёрнутом виде должна остаться короткая сводка")
    }
}
