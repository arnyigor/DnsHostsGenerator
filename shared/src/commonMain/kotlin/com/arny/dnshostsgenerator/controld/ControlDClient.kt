package com.arny.dnshostsgenerator.controld

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Клиент Control D API (docs.controld.com).
 *
 * Правила Control D — это «hosts-файл в облаке с wildcard-ами»: SPOOF-правило
 * (`do = 2`) возвращает для домена заданный IP, то есть ровно то, что мы пишем в hosts.
 * Один запрос умеет создавать правило сразу для пачки доменов (`hostnames[]`), поэтому
 * импорт дешевле, чем у NextDNS с его лимитом 60 запросов в минуту.
 *
 * Особенность: анонимных аккаунтов нет — нужен токен из дашборда
 * (controld.com/dashboard/api, уровень Write).
 */
class ControlDClient(
    private val callFactory: Call.Factory = defaultCallFactory(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Профили аккаунта. PK нужен во всех изменяющих запросах. */
    suspend fun listProfiles(token: String): List<ControlDProfile> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE/profiles")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        val body = execute(token, request, "не удалось получить список профилей")
        parseProfiles(body)
    }

    /**
     * Заливает hosts-записи как SPOOF-правила. Записи группируются по IP:
     * один запрос = один IP + пачка доменов (не больше [MAX_HOSTNAMES_PER_REQUEST]).
     */
    suspend fun importHosts(
        token: String,
        profilePk: String,
        entries: List<HostEntry>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): ControlDImportResult = withContext(Dispatchers.IO) {
        require(profilePk.matches(PROFILE_PK)) { "Некорректный PK профиля: $profilePk" }
        val byIp = entries
            .filter { it.domain.isNotBlank() && it.ip.isNotBlank() }
            .distinctBy { it.domain }
            .groupBy { it.ip }

        var requests = 0
        var created = 0
        var done = 0
        val failures = mutableListOf<String>()

        for ((ip, group) in byIp) {
            group.chunked(MAX_HOSTNAMES_PER_REQUEST).forEach { chunk ->
                val form = buildRulesForm(ip, chunk.map { it.domain })
                val request = Request.Builder()
                    .url("$API_BASE/profiles/$profilePk/rules")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .post(form)
                    .build()
                try {
                    execute(token, request, "не удалось создать правила для $ip")
                    requests++
                    created += chunk.size
                } catch (error: ControlDApiException) {
                    failures += "${error.message}; домены: ${chunk.take(3).joinToString()}…"
                }
                done += chunk.size
                onProgress?.invoke(done, entries.size)
            }
        }
        ControlDImportResult(createdRules = created, requests = requests, failures = failures)
    }

    private fun execute(token: String, request: Request, fallback: String): String {
        callFactory.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code !in 200..299) {
                val parsed = parseApiError(body)
                throw ControlDApiException(
                    message = "${parsed ?: fallback}: HTTP ${response.code}",
                    statusCode = response.code,
                )
            }
            if (body.contains("\"success\":false") || body.contains("\"success\": false")) {
                throw ControlDApiException(parseApiError(body) ?: fallback)
            }
            return body
        }
    }

    private companion object {
        const val API_BASE = "https://api.controld.com"
        const val MAX_HOSTNAMES_PER_REQUEST = 100
        val PROFILE_PK = Regex("^[A-Za-z0-9]{3,32}$")
    }
}

data class ControlDProfile(
    val pk: String,
    val name: String,
)

data class HostEntry(
    val domain: String,
    val ip: String,
)

data class ControlDImportResult(
    val createdRules: Int,
    val requests: Int,
    val failures: List<String>,
)

class ControlDApiException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)

/** SPOOF-правило: `do = 2`, включено, `via` = IP, домены — повторами `hostnames[]`. */
internal fun buildRulesForm(ip: String, hostnames: List<String>): FormBody =
    FormBody.Builder()
        .add("do", "2")
        .add("status", "1")
        .add("via", ip)
        .apply { hostnames.forEach { add("hostnames[]", it) } }
        .build()

/** Ответ `{"success":true,"body":[{"PK":"...","name":"..."}]}` — берём PK и имя. */
internal fun parseProfiles(body: String, json: Json = Json { ignoreUnknownKeys = true; isLenient = true }): List<ControlDProfile> {
    val root = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull()
        ?: return emptyList()
    val array = root["body"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val pk = (obj["PK"] ?: obj["pk"])?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val name = (obj["name"] ?: obj["profileName"])?.jsonPrimitive?.contentOrNull ?: pk
        ControlDProfile(pk = pk, name = name)
    }
}

/** Текст ошибки Control D: `{"success":false,"error":{"code":40001,"message":"..."}}`. */
internal fun parseApiError(
    body: String,
    json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
): String? {
    val root = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull() ?: return null
    val error = runCatching { root["error"]?.jsonObject }.getOrNull() ?: return null
    val message = error["message"]?.jsonPrimitive?.contentOrNull ?: return null
    val code = error["code"]?.jsonPrimitive?.contentOrNull
    return if (code != null) "$message (код $code)" else message
}

/** Разбор hosts-вывода: строки `IP домен`, служебные `#...` пропускаются. */
fun parseHostEntries(text: String): List<HostEntry> =
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) return@mapNotNull null
            val ip = parts[0]
            val domain = parts[1]
            if (!ip.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) return@mapNotNull null
            // 0.0.0.0 в hosts означает блокировку — в SPOOF-правила такое тащить нельзя.
            if (ip == "0.0.0.0") return@mapNotNull null
            HostEntry(domain = domain, ip = ip)
        }
        .toList()

fun defaultCallFactory(): Call.Factory = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
