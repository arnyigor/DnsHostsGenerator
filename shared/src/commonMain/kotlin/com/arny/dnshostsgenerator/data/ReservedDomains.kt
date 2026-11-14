package com.arny.dnshostsgenerator.data

/**
 * Домены, которые нельзя подменять ни при каких группах.
 *
 * Проверено 14.11.2026 живым DoT-запросом: `dns.bezmezhau.com` отдаёт прокси-адрес
 * (143.20.64.55) не только для `gemini.google.com`, но и для `clientservices.googleapis.com`
 * и `o.pki.goog`. Эти адреса обслуживают Play Services (регистрация для push) и проверку
 * отзыва сертификатов Google, поэтому подмена ломает уведомления и TLS далеко за пределами
 * выбранной группы. Аналогично ведут себя проверки связи Microsoft.
 *
 * Фильтр применяется при генерации, поэтому он же защищает уже существующие базы,
 * где старый набор доменов остался в группах.
 */
object ReservedDomains {

    /** Системные эндпоинты: push, проверка связи, OCSP/CRL. */
    val systemDomains: Set<String> = setOf(
        // Google Play Services и транспорт push-уведомлений (FCM).
        "android.googleapis.com",
        "android.clients.google.com",
        "mtalk.google.com",
        "clientservices.googleapis.com",
        "clients4.google.com",
        "play.googleapis.com",
        "firebaseinstallations.googleapis.com",
        "fcm.googleapis.com",
        // Проверка отзыва сертификатов Google: без неё TLS-проверка врёт.
        "o.pki.goog",
        "pki.goog",
        "ocsp.pki.goog",
        "crl.pki.goog",
        // Проверка доступности сети (Android и Windows): подмена даёт «нет интернета».
        "connectivitycheck.gstatic.com",
        "connectivitycheck.android.com",
        "www.msftconnecttest.com",
        "ipv6.msftconnecttest.com",
        "www.msftncsi.com",
        "ipv6.msftncsi.com",
    )

    /** Нормализованный (нижний регистр, без завершающей точки) набор. */
    private val normalized: Set<String> = systemDomains.map { it.lowercase().trimEnd('.') }.toSet()

    fun isReserved(domain: String): Boolean = domain.trim().lowercase().trimEnd('.') in normalized
}
