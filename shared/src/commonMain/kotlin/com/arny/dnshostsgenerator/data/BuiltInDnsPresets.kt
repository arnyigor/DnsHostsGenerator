package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.domain.DnsProviderPreset

object BuiltInDnsPresets {
    const val RECOMMENDED_ID: String = "dns_comss_one"

    val defaults: List<DnsProviderPreset> = listOf(
        DnsProviderPreset(
            id = "dns_bezmezhau_com",
            title = "dns.bezmezhau.com",
            // 23.10.2026: DoT по имени периодически не поднимается (TCP:853 timeout),
            // а по IP 91.108.243.78 — отвечает и возвращает рабочий адрес (31.58.216.201).
            // Поэтому ходим на IP, а сертификат проверяем по имени (dotHost).
            primaryDns = "91.108.243.78",
            dotHost = "dns.bezmezhau.com",
            outputFileName = "host_out_dns.bezmezhau.com.txt",
        ),
        DnsProviderPreset(
            id = "dns_astracat_ru",
            title = "dns.astracat.ru",
            primaryDns = "dns.astracat.ru",
            outputFileName = "host_out_dns.astracat.ru.txt",
        ),
        DnsProviderPreset(
            id = "dns_comss_one",
            title = "dns.comss.one (рекомендуемый)",
            primaryDns = "83.220.169.155",
            dotHost = "dns.comss.one",
            outputFileName = "host_out_dns.comss.one.txt",
        ),
        DnsProviderPreset(
            id = "dns_geohide_ru",
            title = "dns.geohide.ru",
            primaryDns = "dns.geohide.ru",
            outputFileName = "host_out_dns.geohide.ru.txt",
            // 23.10.2026: ответ приходил за 28 с (таймаут запроса 5 с), а вернувшиеся адреса
            // 193.233.112.x не открывались по TCP:443 ни для одного ИИ-домена.
            enabledByDefault = false,
            disabledReason = "отдаёт недостижимые адреса и не укладывается в таймаут (проверено 23.10.2026)",
        ),
        DnsProviderPreset(
            id = "dns_eu_geohide_ru",
            title = "eu.geohide.ru (EU)",
            primaryDns = "eu.geohide.ru",
            outputFileName = "host_out_eu.geohide.ru.txt",
            // 23.10.2026: DoT 0.5–0.8 с, TLS-валидные адреса для chatgpt.com, claude.ai,
            // gemini.google.com и open.spotify.com. Именно EU-ветка работает, в отличие от RU.
        ),
        DnsProviderPreset(
            id = "dns_us_geohide_ru",
            title = "us.geohide.ru (US)",
            primaryDns = "us.geohide.ru",
            outputFileName = "host_out_us.geohide.ru.txt",
            // 23.10.2026: ответ ~7 с, адреса 192.255.159.x/192.3.233.253 не открывали TCP:443.
            enabledByDefault = false,
            disabledReason = "отвечает ~7 с и отдаёт недостижимые адреса (проверено 23.10.2026)",
        ),
        DnsProviderPreset(
            id = "dns_mafioznik_xyz",
            title = "dns.mafioznik.xyz",
            primaryDns = "dns.mafioznik.xyz",
            // Просроченный TLS-сертификат: DoT подключается в trust-all режиме.
            allowInvalidTls = true,
            outputFileName = "host_out_dns.mafioznik.xyz.txt",
        ),
        DnsProviderPreset(
            id = "dns_malw_link",
            title = "dns.malw.link",
            primaryDns = "dns.malw.link",
            outputFileName = "host_out_dns.malw.link.txt",
            // 23.10.2026: DoT отвечает, но 179.254.166.174 не открывал TCP:443 ни для
            // chatgpt.com, ни для gemini/api.anthropic/claude.ai — в hosts попадал мёртвый адрес.
            enabledByDefault = false,
            disabledReason = "отдаёт недостижимый адрес 179.254.166.174 (проверено 23.10.2026)",
        ),
        DnsProviderPreset(
            id = "free_shecan_ir",
            title = "free.shecan.ir",
            primaryDns = "free.shecan.ir",
            // Просроченный TLS-сертификат: DoT подключается в trust-all режиме.
            allowInvalidTls = true,
            outputFileName = "host_out_free.shecan.ir.txt",
        ),
        DnsProviderPreset(
            id = "pro_shecan_ir",
            title = "pro.shecan.ir",
            primaryDns = "pro.shecan.ir",
            outputFileName = "host_out_pro.shecan.ir.txt",
            // 23.10.2026: адреса 50.7.177.x / 94.130.x / 138.201.x не открывались по TCP:443.
            enabledByDefault = false,
            disabledReason = "отдаёт недостижимые адреса (проверено 23.10.2026)",
        ),
        DnsProviderPreset(
            id = "dns_zapret_moe",
            title = "dns.zapret.moe",
            primaryDns = "dns.zapret.moe",
            outputFileName = "host_out_dns.zapret.moe.txt",
        ),
        DnsProviderPreset(
            id = "dns_wlrus_cfd",
            title = "dns.wlrus.cfd",
            primaryDns = "dns.wlrus.cfd",
            // 23.10.2026: сертификат DoT не проходит строгую проверку, поэтому trust-all
            // (как у mafioznik/shecan). Ответ ~100 мс, прокси 191.217.212.16 валиден
            // по TLS для chatgpt.com, claude.ai, gemini.google.com и open.spotify.com.
            allowInvalidTls = true,
            outputFileName = "host_out_dns.wlrus.cfd.txt",
        ),
        DnsProviderPreset(
            id = "xbox_dns_ru",
            title = "xbox-dns.ru",
            primaryDns = "176.99.11.77",
            // DoT xbox-dns.ru отвечает только по hostname (raw IP 176.99.11.77:853 не слушает).
            dotHost = "xbox-dns.ru",
            outputFileName = "host_out_xbox-dns.ru.txt",
            // 12.10.2026 живой DoT-запрос: chatgpt.com / openai.com / gemini.google.com → 0.0.0.0,
            // то есть провайдер обнуляет ИИ-домены, а не разблокирует их. Для игровых доменов
            // он при этом работает, поэтому пресет выключен, а не удалён.
            enabledByDefault = false,
            disabledReason = "отвечает 0.0.0.0 на ИИ-домены (проверено 12.10.2026) — для разблокировки ИИ не подходит",
        ),
    )
}
