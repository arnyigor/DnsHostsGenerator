package com.arny.dnshostsgenerator.dnsvpn

/**
 * Правила подмены для локального DNS.
 *
 * Формат совместим с personalDNSfilter, чтобы один и тот же текст можно было
 * вставить и туда, и сюда:
 * ```
 * >chatgpt.com 45.13.237.213   # подмена IPv4
 * chatgpt.com                  # блокировка остальных ответов (AAAA/HTTPS/SVCB)
 * ```
 */
data class DnsRules(
    /** Домен → IPv4, который отдаём вместо настоящего адреса. */
    val mappings: Map<String, String> = emptyMap(),
    /** Домены, для которых отвечаем «пусто» (аналог строки без `>`). */
    val blocked: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = mappings.isEmpty() && blocked.isEmpty()

    fun mappingFor(domain: String): String? = mappings[domain.lowercase().trimEnd('.')]

    fun isBlocked(domain: String): Boolean = domain.lowercase().trimEnd('.') in blocked

    /** Адрес, который используется во всех подменах; null, если адреса разные или их нет. */
    fun singleIp(): String? = mappings.values.toSet().singleOrNull()

    companion object {
        private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        fun parse(text: String): DnsRules {
            val mappings = LinkedHashMap<String, String>()
            val blocked = LinkedHashSet<String>()
            text.lineSequence().forEach { rawLine ->
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty()) return@forEach
                if (line.startsWith(">")) {
                    val parts = line.removePrefix(">").trim().split(Regex("\\s+"))
                    val host = parts.getOrNull(0)?.lowercase() ?: return@forEach
                    val ip = parts.getOrNull(1) ?: return@forEach
                    if (IPV4.matches(ip)) mappings[host] = ip
                } else {
                    line.split(Regex("\\s+")).firstOrNull()?.let { host ->
                        if (IPV4.matches(host)) return@forEach
                        blocked += host.lowercase()
                    }
                }
            }
            return DnsRules(mappings = mappings, blocked = blocked)
        }
    }
}
