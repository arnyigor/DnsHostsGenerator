package com.arny.dnshostsgenerator.data

import com.arny.dnshostsgenerator.domain.DnsProviderPreset

object BuiltInDnsPresets {
    const val RECOMMENDED_ID: String = "dns_comss_one"

    val defaults: List<DnsProviderPreset> = listOf(
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
            id = "xbox_dns_ru",
            title = "xbox-dns.ru",
            primaryDns = "176.99.11.77",
            // DoT xbox-dns.ru отвечает только по hostname (raw IP 176.99.11.77:853 не слушает).
            dotHost = "xbox-dns.ru",
            outputFileName = "host_out_xbox-dns.ru.txt",
        ),
    )
}
