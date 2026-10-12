package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.presentation.HostsGeneratorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Отключённый пресет — это не удалённый: остальные продолжают работать, а выключенный
 * нельзя выбрать и видно почему. xbox-dns.ru выключен 12.10.2026: живой DoT-запрос
 * вернул 0.0.0.0 на chatgpt.com/openai.com/gemini.google.com.
 */
class DisabledPresetsTest {

    @Test
    fun onlyBrokenPresetIsDisabledAndHasReason() {
        val disabled = BuiltInDnsPresets.defaults.filter { !it.enabledByDefault }

        assertEquals(listOf("xbox_dns_ru"), disabled.map { it.id })
        disabled.forEach { preset ->
            assertTrue(
                !preset.disabledReason.isNullOrBlank(),
                "у отключённого пресета ${preset.id} должна быть причина",
            )
        }
    }

    @Test
    fun otherPresetsStayEnabled() {
        val enabled = BuiltInDnsPresets.defaults.filter { it.enabledByDefault }.map { it.id }

        // Удалять рабочие пресеты нельзя: все остальные остаются включёнными.
        assertEquals(
            listOf(
                "dns_astracat_ru",
                "dns_comss_one",
                "dns_geohide_ru",
                "dns_mafioznik_xyz",
                "dns_malw_link",
                "free_shecan_ir",
                "pro_shecan_ir",
            ),
            enabled,
        )
    }

    @Test
    fun disabledPresetNeverReachesGeneration() {
        val state = HostsGeneratorState(selectedPresetIds = setOf("xbox_dns_ru"))

        assertTrue(state.selectedPresets.isEmpty())

        // Рекомендуемый пресет по умолчанию работает.
        assertEquals(listOf("dns_comss_one"), HostsGeneratorState().selectedPresets.map { it.id })
    }
}
