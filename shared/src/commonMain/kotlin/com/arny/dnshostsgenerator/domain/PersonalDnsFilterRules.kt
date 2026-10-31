package com.arny.dnshostsgenerator.domain

/**
 * Правила для personalDNSfilter (`additionalHosts.txt`).
 *
 * Два блока обязательны:
 * 1. `>host ip` — собственно подмена адреса;
 * 2. `host` без префикса — блокировка остальных ответов для этого имени (AAAA/SVCB/HTTPS),
 *    иначе клиент может уйти на настоящий адрес в обход подмены.
 *
 * Важно: строка блокировки действует и на все поддомены. Поэтому одиночная запись
 * `chatgpt.com` обнуляет `ws.chatgpt.com` и `codex-cloud-backend.chatgpt.com` —
 * проверено в журнале personalDNSfilter 28.10.2026 (ответ `0.0.0.0`), из-за чего
 * приложение ChatGPT не работало, хотя сайт открывался. Каждый поддомен должен быть
 * перечислен явно.
 */
fun buildPersonalDnsFilterRules(profile: AppProfile, ip: String): String = buildString {
    appendLine("# ${profile.title}: подмена адресов (DnsHostsGenerator)")
    appendLine("# Вставьте в additionalHosts.txt personalDNSfilter.")
    appendLine("# Строки без '>' блокируют остальные ответы, включая поддомены — они обязательны.")
    profile.hosts.forEach { host -> appendLine(">$host $ip") }
    appendLine()
    profile.hosts.forEach { host -> appendLine(host) }
}

/** Правила из карты «домен → адрес»: у каждого домена может быть свой прокси. */
fun buildPersonalDnsFilterRules(entries: Map<String, String>, title: String): String = buildString {
    appendLine("# $title: подмена адресов (DnsHostsGenerator)")
    appendLine("# Вставьте в additionalHosts.txt personalDNSfilter или в поле правил DNS-VPN.")
    appendLine("# Строки без '>' блокируют остальные ответы, включая поддомены — они обязательны.")
    entries.forEach { (host, ip) -> appendLine(">$host $ip") }
    appendLine()
    entries.keys.forEach { host -> appendLine(host) }
}
