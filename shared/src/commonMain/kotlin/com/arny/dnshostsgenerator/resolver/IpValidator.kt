package com.arny.dnshostsgenerator.resolver

/**
 * Результат проверки адреса из DNS-ответа.
 *
 * [UNKNOWN] нужен отдельно от [REJECTED]: медленный прокси может отдать TLS-хендшейк только
 * через 10 с (замер 23.10.2026: 199.189.249.126 для gemini.google.com), и такой адрес нельзя
 * ни принимать сразу, ни выбрасывать — его надо перепроверить с большим таймаутом.
 */
enum class IpCheckResult {
    SERVES,
    REJECTED,
    UNKNOWN,
}

/**
 * Данные сертификата, полученного при успешном TLS-хендшейке.
 *
 * Нужны для UI: просроченный или истекающий сертификат объясняет, почему адрес перестанет
 * работать, а эмитент помогает отличить настоящий узел от подставного прокси.
 */
data class CertificateFacts(
    val issuer: String,
    val notAfterEpochMillis: Long,
)

/** Итог проверки адреса вместе с данными сертификата (сертификат есть только при [IpCheckResult.SERVES]). */
data class IpCheckOutcome(
    val result: IpCheckResult,
    val certificate: CertificateFacts? = null,
)

/**
 * Проверка, что IP из DNS-ответа действительно обслуживает домен.
 *
 * Зачем: DNS-пресет может ответить (rcode=0), но вернуть адрес, до которого нет доступа.
 * Измерено 23.10.2026: `dns.malw.link` отдавал 179.254.166.174, а TCP:443 на него не
 * открывался ни для одного ИИ-домена; `dns.comss.one` на chatgpt.com возвращает
 * два IP, один из которых — общий прокси провайдера, а не адрес, выбранный первым.
 *
 * Критерий: TLS-хендшейк на 443 с SNI = домен и валидным сертификатом на этот домен.
 * Это необходимый (но не достаточный: DPI может рвать уже сессию) признак работоспособности.
 */
interface IpValidator {
    suspend fun check(domain: String, ip: String, timeoutMillis: Int = 4_000): IpCheckResult

    /**
     * То же, что [check], но с данными сертификата. Реализации, которым сертификат не нужен,
     * наследуют это поведение и отдают только результат проверки.
     */
    suspend fun inspect(
        domain: String,
        ip: String,
        timeoutMillis: Int = 4_000,
    ): IpCheckOutcome = IpCheckOutcome(check(domain, ip, timeoutMillis))
}

expect fun createIpValidator(): IpValidator
