package com.arny.dnshostsgenerator.platform

/**
 * Отдаёт текст во внешние приложения (Android — через «Поделиться»,
 * desktop — в буфер обмена). Нужно, чтобы правила для DNS-VPN можно было
 * сразу вставить в другое приложение, а не копировать вручную.
 */
expect fun shareText(subject: String, text: String): Boolean
