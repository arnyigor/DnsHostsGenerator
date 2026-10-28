package com.arny.dnshostsgenerator.controld

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Живая проверка контракта с api.controld.com (сеть обязательна):
 *
 *     DNSHOSTS_LIVE=1 ./gradlew :shared:jvmTest --tests "*LiveControlDTest*" --rerun-tasks
 *
 * Без токена проверяем только обработку ошибки: сервер обязан ответить
 * `Invalid session` в нашем формате, а не таймаутом — значит URL, заголовок
 * Authorization и разбор ошибок совпадают с реальным API.
 */
class LiveControlDTest {

    private val enabled = System.getenv("DNSHOSTS_LIVE") == "1"

    @Test
    fun listProfiles_withInvalidTokenReturnsApiError() = runBlocking {
        if (!enabled) {
            println("LiveControlDTest пропущен: DNSHOSTS_LIVE != 1")
            return@runBlocking
        }
        val client = ControlDClient()
        val error = assertFailsWith<ControlDApiException> {
            client.listProfiles("invalid-token-for-test")
        }
        println("ControlD ответил: ${error.message} (HTTP ${error.statusCode})")
        assertTrue(
            error.message.orEmpty().contains("Invalid session", ignoreCase = true),
            "ожидали сообщение Control D об ошибке токена, получили: ${error.message}",
        )
    }
}
