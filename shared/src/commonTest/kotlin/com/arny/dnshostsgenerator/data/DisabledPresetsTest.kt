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

        // 23.10.2026 по живым замерам отключены провайдеры, которые либо не отвечают,
        // либо отдают недостижимые адреса: geohide, malw.link, pro.shecan.ir (+ xbox-dns.ru).
        assertEquals(
            listOf(
                // 14.11.2026: DoT на 83.220.169.155 обрывается EOF, подбор адреса не проходит.
                "dns_comss_one",
                "dns_geohide_ru",
                "dns_us_geohide_ru",
                "dns_malw_link",
                "pro_shecan_ir",
                // 08.11.2026: DoT не отвечает, адрес из DoH не открывает TCP:443.
                "dns_zapret_moe",
                // 08.11.2026 по сводке 4PDA (пост 145952319): DoH недоступен / адрес недостижим.
                "api_magicdns_ru",
                "dns_dagami_ru",
                "xbox_dns_ru",
            ),
            disabled.map { it.id },
        )
        disabled.forEach { preset ->
            assertTrue(
                !preset.disabledReason.isNullOrBlank(),
                "у отключённого пресета ${preset.id} должна быть причина",
            )
        }
    }

    @Test
    fun otherPresetsStayEnabled() {
        // Проверено 08.11.2026 живым DoT-запросом (dnsjava): dns.dns-ai.ru -> 62.60.230.61,
        // dns.geodema.love -> 179.236.130.11, dns.keyoneday.ru -> 2.58.127.63.
        val enabled = BuiltInDnsPresets.defaults.filter { it.enabledByDefault }.map { it.id }

        // Удалять рабочие пресеты нельзя: все остальные остаются включёнными.
        // dns.bezmezhau.com и dns.zapret.moe добавлены 12.10.2026 по итогам разбора темы:
        // DoT со строгой проверкой сертификата проверен живым запросом, на chatgpt/openai/gemini
        // они отвечают рабочим прокси-адресом.
        assertEquals(
            listOf(
                "dns_bezmezhau_com",
                "dns_astracat_ru",
                "dns_eu_geohide_ru",
                "dns_mafioznik_xyz",
                "free_shecan_ir",
                "dns_wlrus_cfd",
                // 08.11.2026: DoT отвечает валидным сертификатом и отдаёт прокси-адреса.
                "dns_dns_ai_ru",
                "dns_geodema_love",
                "dns_keyoneday_ru",
            ),
            enabled,
        )
    }

    @Test
    fun disabledPresetNeverReachesGeneration() {
        val state = HostsGeneratorState(selectedPresetIds = setOf("xbox_dns_ru"))

        assertTrue(state.selectedPresets.isEmpty())

        // Рекомендуемый пресет по умолчанию работает.
        // 14.11.2026: рекомендуемым стал bezmezhau — comss.one перестал отвечать по DoT.
        assertEquals(listOf("dns_bezmezhau_com"), HostsGeneratorState().selectedPresets.map { it.id })
    }
}
