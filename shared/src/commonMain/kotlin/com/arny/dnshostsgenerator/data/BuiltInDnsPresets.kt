package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.domain.DnsProviderPreset

object BuiltInDnsPresets {
    // 14.11.2026: dns.comss.one перестал отвечать по DoT (EOF-обрыв на телефоне и отказ
    // соединения с ПК), поэтому рекомендуемым стал bezmezhau: его адрес 31.58.216.201
    // отвечает 200 на всех 13 доменах профиля ChatGPT.
    const val RECOMMENDED_ID: String = "dns_bezmezhau_com"

    val defaults: List<DnsProviderPreset> = listOf(
        DnsProviderPreset(
            id = "dns_bezmezhau_com",
            title = "dns.bezmezhau.com (рекомендуемый)",
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
            title = "dns.comss.one",
            primaryDns = "83.220.169.155",
            dotHost = "dns.comss.one",
            outputFileName = "host_out_dns.comss.one.txt",
            // 14.11.2026: DoT на 83.220.169.155 обрывается EOF-ошибкой и на телефоне
            // (logcat 22:43–22:54), и с ПК (connection refused). Пока адрес лежит, пресет
            // не может ни разрешить домены, ни подобрать адрес профиля.
            enabledByDefault = false,
            disabledReason = "DoT не отвечает: EOF-обрыв и отказ соединения (проверено 14.11.2026)",
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
            enabledByDefault = false,
            // 08.11.2026: DoT TCP:853 не отвечает (таймаут), публичную работу по DoT отключили
            // с 03.11.2026; DoH отдаёт 5.129.221.64, который не открывает TCP:443.
            disabledReason = "DoT не отвечает, адрес из DoH недостижим (проверено 08.11.2026)",
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
        // Ниже — пресеты из сводки 4PDA (пост 145952319, 08.11.2026).
        // Живая проверка 08.11.2026 (DoH POST, TLS по SNI на 443):
        //  dns-ai/geodema/keyoneday/comss отдают разные рабочие адреса на chatgpt/gemini;
        //  dagami/wlrus/zapret отдали адрес, не открывающий TCP:443; magicdns/bezmezhau/eu.geohide — таймаут DoH.
        DnsProviderPreset(
            id = "dns_dns_ai_ru",
            title = "dns.dns-ai.ru",
            primaryDns = "dns.dns-ai.ru",
            outputFileName = "host_out_dns.dns-ai.ru.txt",
            // 08.11.2026 (DoT, dnsjava): chatgpt.com -> 62.60.230.61, gemini.google.com -> 109.120.131.200,
            // сертификат DoT валиден (Let's Encrypt). До 17 доменов прокси может включать не все.
        ),
        DnsProviderPreset(
            id = "dns_geodema_love",
            title = "dns.geodema.love (бета)",
            primaryDns = "dns.geodema.love",
            outputFileName = "host_out_dns.geodema.love.txt",
            // 08.11.2026: chatgpt.com -> 179.236.130.11 (TLS валиден), gemini -> настоящие адреса Google.
        ),
        DnsProviderPreset(
            id = "dns_keyoneday_ru",
            title = "dns.keyoneday.ru",
            primaryDns = "dns.keyoneday.ru",
            outputFileName = "host_out_dns.keyoneday.ru.txt",
            // 08.11.2026: отдаёт один и тот же адрес 2.58.127.63 на всё; TLS валиден.
        ),
        DnsProviderPreset(
            id = "api_magicdns_ru",
            title = "api.magicdns.ru",
            primaryDns = "api.magicdns.ru",
            outputFileName = "host_out_api.magicdns.ru.txt",
            enabledByDefault = false,
            // 08.11.2026: DoH 109.120.177.170/45.86.65.181 — таймаут соединения.
            disabledReason = "DoH-сервер не отвечает (проверено 08.11.2026)",
        ),
        DnsProviderPreset(
            id = "dns_dagami_ru",
            title = "dns.dagami.ru",
            primaryDns = "dns.dagami.ru",
            outputFileName = "host_out_dns.dagami.ru.txt",
            enabledByDefault = false,
            // 08.11.2026: адрес 31.76.52.235 не открывает TCP:443.
            disabledReason = "отдаёт недостижимый адрес (проверено 08.11.2026)",
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
