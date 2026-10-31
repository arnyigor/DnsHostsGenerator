package com.arny.dnshostsgenerator.domain

/**
 * Набор доменов, которые приложение спрашивает при работе.
 *
 * Проверять один домен мало: `chatgpt.com` открывается в браузере, но Android-приложение
 * дополнительно ходит в `android.chat.openai.com`, `ws.chatgpt.com`, `ab.chatgpt.com`,
 * `codex-cloud-backend.chatgpt.com`, `aw.api.openai.com`, `cdn.platform.openai.com`.
 * Если подменить только `chatgpt.com`, приложение продолжает работать «как раньше», то есть не работает.
 */
data class AppProfile(
    val id: String,
    val title: String,
    val hosts: List<String>,
)

object BuiltInAppProfiles {

    /**
     * Домены Android-приложения ChatGPT. Список снят с журнала запросов personalDNSfilter
     * на реальном устройстве 31.10.2026.
     */
    val chatGptAndroid: AppProfile = AppProfile(
        id = "app_chatgpt_android",
        title = "ChatGPT (Android)",
        hosts = listOf(
            // Основной интерфейс и websockets.
            "chatgpt.com",
            "ws.chatgpt.com",
            "ab.chatgpt.com",
            "codex-cloud-backend.chatgpt.com",
            // Мобильный API приложения.
            "android.chat.openai.com",
            "aw.api.openai.com",
            "cdn.platform.openai.com",
            // Вход и аккаунт: без них после запроса логина сервис отвечает
            // «регион не поддерживается» — проверено 31.10.2026 на телефоне.
            "auth.openai.com",
            "auth0.openai.com",
            "cdn.auth0.com",
            "chat.openai.com",
            "ios.chat.openai.com",
            "api.openai.com",
            "platform.openai.com",
            // Статика и файлы.
            "cdn.oaistatic.com",
            "files.oaiusercontent.com",
            "openai.com",
        ),
    )

    val all: List<AppProfile> = listOf(chatGptAndroid)
}
