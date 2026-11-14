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

    /**
     * Домены Gemini в браузере и в приложении.
     *
     * Проверено 14.11.2026 на телефоне и с ПК: все адреса ниже обслуживает один и тот же
     * прокси-адрес `dns.dns-ai.ru` (181.215.40.49) — 200/302/404 на каждый хост. Поэтому
     * для Gemini схема «один адрес на все домены» работает, в отличие от ChatGPT,
     * где адреса у провайдера разные.
     */
    val geminiAndroid: AppProfile = AppProfile(
        id = "app_gemini_android",
        title = "Gemini (Android)",
        hosts = listOf(
            // Основной интерфейс и AI Studio.
            "gemini.google.com",
            "aistudio.google.com",
            "makersuite.google.com",
            "bard.google.com",
            // API генеративного языка.
            "generativelanguage.googleapis.com",
            "aisandbox-pa.googleapis.com",
            "assistant-s3-pa.googleapis.com",
            "proactivebackend-pa.googleapis.com",
            "robinfrontend-pa.googleapis.com",
            // Служебные каналы страницы Gemini (websockets и телеметрия запросов).
            "alkalicore-pa.clients6.google.com",
            "alkalimakersuite-pa.clients6.google.com",
            "webchannel-alkalimakersuite-pa.clients6.google.com",
            "people-pa.clients6.google.com",
            "signaler-pa.clients6.google.com",
            "waa-pa.clients6.google.com",
            // Соседние продукты Google AI, которые открываются из того же меню.
            "deepmind.google",
            "jules.google.com",
            "notebooklm.google.com",
            "labs.google",
        ),
    )

    val all: List<AppProfile> = listOf(chatGptAndroid, geminiAndroid)

    fun byId(id: String?): AppProfile = all.firstOrNull { it.id == id } ?: chatGptAndroid
}
