package com.arny.dnshostsgenerator.domain

/**
 * Форматы, в которых один и тот же список распространяют в теме 4PDA «DNS CLUB»
 * (паки dnscomssone/xboxdnsru: aihosts.txt, aihosts.conf, adguard.txt, nextdns.txt).
 * HOSTS — родной формат приложения, остальные собираются из успешно разрешённых строк.
 */
enum class ExportFormat(val label: String) {
    HOSTS("hosts"),
    DNSMASQ("dnsmasq .conf"),
    ADGUARD("AdGuard"),
    NEXTDNS("NextDNS"),
}

/**
 * Текст результата в выбранном формате. Кроме hosts содержит только resolved-строки:
 * #forwarded/#unresolved/#duplicate — служебные пометки hosts-вывода и в чужие форматы
 * не переносятся.
 */
fun GenerationResult.exportText(format: ExportFormat): String {
    if (format == ExportFormat.HOSTS) return outputText
    val resolved = lines.filterIsInstance<HostLine.Resolved>()
    val header = "# DnsHostsGenerator: $presetTitle — " +
        "resolved ${stats.resolvedCount}, forwarded ${stats.forwardedCount}, " +
        "unresolved ${stats.unresolvedCount}"
    val body = resolved.map { line ->
        when (format) {
            ExportFormat.DNSMASQ -> "server=/${line.domain}/${line.ip}"
            ExportFormat.ADGUARD -> "||${line.domain}^\$dnsrewrite=${line.ip}"
            ExportFormat.NEXTDNS -> "${line.ip} ${line.domain}"
            ExportFormat.HOSTS -> line.toOutputLine()
        }
    }
    return (listOf(header) + body).joinToString("\n")
}

/**
 * Имя файла в стиле пакетов сообщества: база от [GenerationResult.outputFileName]
 * плюс расширение формата (`host_out_dns.comss.one.conf`, `…adguard.txt`, `…nextdns.txt`).
 */
fun GenerationResult.exportFileName(format: ExportFormat): String {
    val base = outputFileName.removeSuffix(".txt")
    return when (format) {
        ExportFormat.HOSTS -> outputFileName
        ExportFormat.DNSMASQ -> "$base.conf"
        ExportFormat.ADGUARD -> "$base.adguard.txt"
        ExportFormat.NEXTDNS -> "$base.nextdns.txt"
    }
}
