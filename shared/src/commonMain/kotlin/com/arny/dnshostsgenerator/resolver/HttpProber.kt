package com.arny.dnshostsgenerator.resolver

/**
 * Вердикт HTTP-уровня после успешного TLS-хендшейка.
 *
 * TLS-проверки недостаточно: адрес может обслуживать домен (сертификат валиден), но сервис
 * отказывает по своим правилам. Замерено на телефоне 31.10.2026 для `android.chat.openai.com`:
 * настоящие адреса Cloudflare отдают `unsupported_country_region_territory`, а все проверенные
 * прокси-адреса — `{"cf_details":"Request is not allowed"}`, хотя для `chatgpt.com` те же
 * адреса отвечают 200. Без различения этих ответов нельзя понять, годен ли адрес.
 */
enum class HttpVerdict {
    /** Ответ получен и не является отказом по региону/правилам Cloudflare. */
    OK,

    /** `unsupported_country_region_territory` — сервис отказывает по региону адреса. */
    REGION_BLOCKED,

    /** `Request is not allowed` — Cloudflare отклонил запрос с этого адреса. */
    CLOUDFLARE_BLOCKED,

    /** Капча/антибот-страница: адрес работает, но сервис требует ручного прохождения. */
    CHALLENGE,

    /** Соединение не установилось или ответ не получен. */
    FAILED,
}

data class HttpProbeResult(
    val verdict: HttpVerdict,
    val statusCode: Int? = null,
    val elapsedMs: Long = 0,
    val detail: String? = null,
) {
    /** Адрес пригоден для правила подмены: сервер ответил и не отказал по региону/правилам. */
    val isUsable: Boolean get() = verdict == HttpVerdict.OK
}

/** HTTP-проверка адреса: запрос к [host] по адресу [ip] с проверкой сертификата по имени. */
interface HttpProber {
    suspend fun probe(
        host: String,
        ip: String,
        path: String = "/",
        timeoutMillis: Int = 8_000,
    ): HttpProbeResult
}

expect fun createHttpProber(): HttpProber
