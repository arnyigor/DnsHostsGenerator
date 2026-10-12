package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.domain.DnsProviderPreset

object BuiltInDnsPresets {
    const val RECOMMENDED_ID: String = "dns_comss_one"

    val defaults: List<DnsProviderPreset> = listOf(
        DnsProviderPreset(
            id = "dns_bezmezhau_com",
            title = "dns.bezmezhau.com",
            // Адрес сервера неизвестен — как и у astracat/geohide/malw, ходим по hostname:
            // DoT (853) со строгой проверкой сертификата проверен живым запросом 12.10.2026.
            primaryDns = "dns.bezmezhau.com",
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
        ),
        DnsProviderPreset(
            id = "dns_zapret_moe",
            title = "dns.zapret.moe",
            primaryDns = "dns.zapret.moe",
            outputFileName = "host_out_dns.zapret.moe.txt",
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
