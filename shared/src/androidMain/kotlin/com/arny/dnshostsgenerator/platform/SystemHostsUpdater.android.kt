package com.arny.dnshostsgenerator.platform

actual val canUpdateSystemHosts: Boolean = false

actual suspend fun updateSystemHosts(content: String): SaveFileResult = SaveFileResult(
    success = false,
    message = "Прямое обновление системного hosts доступно только в desktop-версии.",
)
