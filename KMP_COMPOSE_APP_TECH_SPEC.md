# Подробное техническое задание: KMP Compose приложение для генерации hosts-файлов DNS/SNI Proxy

## 1. Назначение документа

Этот документ описывает, как перенести текущую логику набора файлов `dns-sni-proxy-hosts-generator` в KMP Compose приложение.

Цель приложения — дать пользователю удобный интерфейс для генерации списка hosts-записей вида:

```text
80.74.29.235 chatgpt.com
80.74.29.235 sora.chatgpt.com
#forwarded cdn.auth0.com
#unresolvedDomain example.com
#duplicate cdn.auth0.com
```

Итоговый список должен быть доступен:

1. для просмотра в приложении;
2. для копирования в буфер обмена;
3. для экспорта в `.txt` файл, например:

```text
host_out_dns.comss.one.txt
```

Приложение должно повторять существующую бизнес-логику PowerShell/CMD файлов, но реализовать её кроссплатформенно на Kotlin Multiplatform + Compose Multiplatform.

---

## 2. Нужно ли брать исходные файлы вместе с ТЗ

### Короткий ответ

Для разработки приложения одного ТЗ достаточно, чтобы понять логику. Но для точного воспроизведения текущего поведения желательно взять вместе с ТЗ минимум следующие файлы:

```text
domainlist.txt
dns-sni-proxy-hosts-generator.ps1
dns-sni-proxy-hosts-generator.cmd
start_*.cmd
host_out_*.txt
```

### Минимальный набор файлов для приложения

Если приложение нужно написать с нуля по этому ТЗ, но при этом использовать текущий список доменов, обязательно нужен:

```text
domainlist.txt
```

Без него приложение можно написать, но в нём не будет исходной базы доменов.

### Рекомендуемый набор файлов для переноса

```text
KMP_COMPOSE_APP_TECH_SPEC.md
KMP_COMPOSE_APP_ANALYSIS.md
domainlist.txt
dns-sni-proxy-hosts-generator.ps1
dns-sni-proxy-hosts-generator.cmd
merge_hosts.ps1
merge_hosts.cmd
start_ALL.cmd
start_dns.astracat.ru.cmd
start_dns.comss.one.cmd
start_dns.geohide.ru.cmd
start_dns.mafioznik.xyz.cmd
start_dns.malw.link.cmd
start_free.shecan.ir.cmd
start_pro.shecan.ir.cmd
start_xbox-dns.ru.cmd
host_out_dns.astracat.ru.txt
host_out_dns.comss.one.txt
host_out_dns.geohide.ru.txt
host_out_xbox-dns.ru.txt
```

### Что обязательно, а что опционально

| Файл | Нужен для чего | Обязательность |
|---|---|---|
| `domainlist.txt` | исходные домены и секции | обязательно |
| `dns-sni-proxy-hosts-generator.ps1` | эталон алгоритма | желательно |
| `dns-sni-proxy-hosts-generator.cmd` | описание CLI-параметров | желательно |
| `start_*.cmd` | DNS-пресеты | желательно |
| `host_out_*.txt` | тестовые эталоны/примеры результата | желательно |
| `merge_hosts.ps1` | логика CSV-сравнения | опционально |
| `merge_hosts.cmd` | обёртка merge-скрипта | опционально |
| `KMP_COMPOSE_APP_ANALYSIS.md` | дополнительный анализ | опционально |

---

## 3. Текущая предметная область

Сейчас проект — это Windows-скрипты для генерации hosts-файлов для SNI Proxy.

Пользователь задаёт:

1. основной DNS-сервер;
2. проверочный DNS-сервер;
3. файл со списком доменов;
4. имя выходного hosts-файла;
5. флаг дедупликации.

Скрипт проходит по доменам, резолвит их через основной DNS и сравнивает ответ с проверочным DNS. Если основной DNS возвращает IP, отличающиеся от проверочного DNS, домен попадает в hosts-файл. Если IP совпадают, домен помечается как `#forwarded` и не добавляется как активная hosts-запись.

---

## 4. Существующие файлы и их роль

### 4.1 `domainlist.txt`

Файл содержит список доменов и комментарии-секции.

Формат:

```text
# --- OpenAI ChatGPT ---
cdn.auth0.com
ab.chatgpt.com
chatgpt.com

# --- Google Gemini AI ---
gemini.google
ai.google.dev
```

Особенности:

- комментарий — строка, которая после удаления ведущих пробелов начинается с `#`;
- пустая строка должна сохраняться в результате;
- обычная непустая строка без `#` считается доменом;
- текущий файл содержит 408 строк;
- доменных строк — 349;
- комментариев — 30;
- пустых строк — 29;
- есть 1 точный дубль: `cdn.auth0.com`.

### 4.2 `dns-sni-proxy-hosts-generator.ps1`

Главный PowerShell-скрипт. Содержит основную бизнес-логику генерации hosts.

Принимает параметры:

```powershell
param(
    [string]$primaryDns,
    [string]$checkDns,
    [string]$domainsFile,
    [string]$outputFile,
    [int]$dedup = 0
)
```

### 4.3 `dns-sni-proxy-hosts-generator.cmd`

CMD-обёртка. Проверяет аргументы, задаёт файл результата по умолчанию, обрабатывает `dedup`, запускает PowerShell.

Формат запуска:

```cmd
dns-sni-proxy-hosts-generator.cmd <primary_DNS> <check_DNS> <domains_file> [output_file] [dedup]
```

Примеры:

```cmd
dns-sni-proxy-hosts-generator.cmd dns.comss.one 8.8.8.8 domainlist.txt
dns-sni-proxy-hosts-generator.cmd dns.comss.one 8.8.8.8 domainlist.txt dedup
dns-sni-proxy-hosts-generator.cmd 176.99.11.77 8.8.8.8 domainlist.txt myhosts.txt
dns-sni-proxy-hosts-generator.cmd 176.99.11.77 8.8.8.8 domainlist.txt myhosts.txt dedup
```

### 4.4 `start_*.cmd`

Готовые пресеты запуска для разных DNS.

Их нужно перенести в приложение как встроенные DNS-профили.

### 4.5 `start_ALL.cmd`

Запускает все `start_*.cmd` одновременно в отдельных окнах.

В приложении аналогом должна быть функция:

```text
Сгенерировать для всех DNS-провайдеров
```

### 4.6 `merge_hosts.ps1`

Сравнивает несколько готовых `host_out_*.txt` файлов и создаёт CSV-таблицу.

Текущая логика:

1. ожидает список заранее заданных файлов;
2. проверяет, что каждый файл существует;
3. читает все файлы;
4. берёт минимальное количество строк среди файлов;
5. создаёт CSV с разделителем `;`;
6. каждая колонка — один DNS-провайдер;
7. каждая строка — соответствующая строка из каждого файла.

Важно: скрипт создаёт файл `combined_hosts.csv`, а CMD-обёртка сообщает `hosts_comparison.csv`. Это несоответствие нужно исправить в приложении.

---

## 5. DNS-пресеты из текущих CMD-файлов

Приложение должно иметь встроенные пресеты.

| ID | Название | Primary DNS | Check DNS | Output filename |
|---|---|---|---|---|
| `dns_astracat_ru` | dns.astracat.ru | `dns.astracat.ru` | `8.8.8.8` | `host_out_dns.astracat.ru.txt` |
| `dns_comss_one` | dns.comss.one | `dns.comss.one` | `8.8.8.8` | `host_out_dns.comss.one.txt` |
| `dns_geohide_ru` | dns.geohide.ru | `dns.geohide.ru` | `8.8.8.8` | `host_out_dns.geohide.ru.txt` |
| `dns_mafioznik_xyz` | dns.mafioznik.xyz | `dns.mafioznik.xyz` | `8.8.8.8` | `host_out_dns.mafioznik.xyz.txt` |
| `dns_malw_link` | dns.malw.link | `dns.malw.link` | `8.8.8.8` | `host_out_dns.malw.link.txt` |
| `free_shecan_ir` | free.shecan.ir | `free.shecan.ir` | `8.8.8.8` | `host_out_free.shecan.ir.txt` |
| `pro_shecan_ir` | pro.shecan.ir | `pro.shecan.ir` | `8.8.8.8` | `host_out_pro.shecan.ir.txt` |
| `xbox_dns_ru` | xbox-dns.ru | `176.99.11.77` | `8.8.8.8` | `host_out_xbox-dns.ru.txt` |

Дополнительно в CMD есть закомментированные альтернативы. Их можно добавить как неактивные/дополнительные пресеты:

| Название | Primary DNS | Output filename |
|---|---|---|
| router.comss.one | `router.comss.one` | `host_out_router.comss.one.txt` |
| dns2.mafioznik.xyz | `dns2.mafioznik.xyz` | `host_out_dns2.mafioznik.xyz.txt` |
| Cloudflare Gateway custom | `5u35p8m9i7.cloudflare-gateway.com` | `host_out_5u35p8m9i7.cloudflare-gateway.com.txt` |
| xbox-dns alternative | `80.78.247.254` | `host_out_xbox-dns.ru.txt` |

---

## 6. Главная логика генерации hosts

### 6.1 Входные данные генерации

```kotlin
data class GenerateHostsRequest(
    val primaryDns: String,
    val checkDns: String,
    val inputLines: List<String>,
    val outputFileName: String,
    val dedupEnabled: Boolean,
    val recordType: DnsRecordType = DnsRecordType.A,
    val ipSelectionStrategy: IpSelectionStrategy = IpSelectionStrategy.First,
    val preserveComments: Boolean = true,
    val preserveBlankLines: Boolean = true
)
```

Для полной совместимости с текущими скриптами значения должны быть:

```text
recordType = A
ipSelectionStrategy = First
preserveComments = true
preserveBlankLines = true
dedupEnabled = true для start_*.cmd
```

### 6.2 Обработка каждой строки

Алгоритм должен идти строго по порядку строк входного файла.

Псевдокод:

```text
lineCount = 0
resolvedCount = 0
failedCount = 0
forwardedCount = 0
duplicateCount = 0
processedDomains = empty set
outputLines = empty list

for each line in inputLines:
    lineCount++

    if line is null/blank/whitespace only:
        outputLines.add("")
        continue

    if line.trimStart().startsWith("#"):
        outputLines.add(line)
        continue

    domain = line.trim()

    if dedupEnabled and processedDomains contains domain:
        duplicateCount++
        outputLines.add("#duplicate " + domain)
        continue

    try primary resolve A records
        primaryRecords = only A records

        if primaryRecords is empty:
            failedCount++
            outputLines.add("#unresolvedDomain " + domain)
            continue

        firstIp = first IP from primaryRecords
        primaryIps = all IPs from primaryRecords
        processedDomains.add(domain)

        try check resolve A records
            checkRecords = only A records

            if checkRecords is not empty:
                checkIps = all IPs from checkRecords
                hasCommon = any primary IP equals any check IP

                if hasCommon:
                    forwardedCount++
                    outputLines.add("#forwarded " + domain)
                else:
                    resolvedCount++
                    outputLines.add(firstIp + " " + domain)
            else:
                resolvedCount++
                outputLines.add(firstIp + " " + domain)

        catch check resolve error:
            resolvedCount++
            outputLines.add(firstIp + " " + domain)

    catch primary resolve error:
        failedCount++
        outputLines.add("#unresolvedDomain " + domain)
```

### 6.3 Важная особенность дедупликации

В текущем PowerShell-скрипте домен добавляется в `processedDomains` только после успешного получения A-записей от основного DNS.

Это означает:

- если домен был успешно разрешён, его следующий повтор станет `#duplicate`;
- если домен не разрешился, он не добавляется в `processedDomains`;
- повтор такого неразрешённого домена будет снова резолвиться, а не автоматически считаться дублем.

Это поведение нужно сохранить в режиме совместимости.

### 6.4 Правила комментариев

Строка считается комментарием, если:

```text
line.trimStart().startsWith("#")
```

Комментарий должен попадать в выходной файл без изменений.

Пример:

```text
# --- OpenAI ChatGPT ---
```

остаётся:

```text
# --- OpenAI ChatGPT ---
```

### 6.5 Правила пустых строк

Если строка пустая или состоит только из пробелов/табов, в выходной файл добавляется пустая строка:

```text

```

### 6.6 Правила доменных строк

Для доменной строки используется:

```text
domain = line.trim()
```

То есть пробелы по краям удаляются.

В режиме полной совместимости не нужно дополнительно менять:

- регистр;
- trailing dot;
- punycode;
- IDN;
- wildcard;
- внутренние пробелы.

Расширенная нормализация может быть дополнительной опцией, но не должна ломать режим совместимости.

---

## 7. DNS-логика

### 7.1 Основной DNS

Основной DNS — это DNS-сервер, который должен вернуть IP для hosts-записи.

Пример:

```text
primaryDns = dns.comss.one
```

Запрос:

```text
resolve A chatgpt.com via dns.comss.one
```

Используются только IPv4 A-записи.

### 7.2 Проверочный DNS

Проверочный DNS нужен для определения, отличается ли ответ основного DNS от обычного публичного DNS.

Обычно:

```text
checkDns = 8.8.8.8
```

### 7.3 Логика `#forwarded`

Если основной DNS и проверочный DNS имеют хотя бы один общий IP, домен считается forwarded.

Пример:

```text
primaryIps = ["1.1.1.1", "2.2.2.2"]
checkIps   = ["2.2.2.2", "3.3.3.3"]
```

Есть общий IP `2.2.2.2`, значит результат:

```text
#forwarded domain.example
```

### 7.4 Логика успешного resolved

Если основной DNS вернул A-записи, а проверочный DNS:

- не ответил;
- вернул ошибку;
- не вернул A-записей;
- вернул IP без пересечения с основным DNS;

то домен считается успешно разрешённым.

В выходной файл записывается первый IP от основного DNS:

```text
firstPrimaryIp domain.example
```

Пример:

```text
80.74.29.235 chatgpt.com
```

### 7.5 Логика `#unresolvedDomain`

Если основной DNS:

- не ответил;
- вернул ошибку;
- не вернул A-записей;

то результат:

```text
#unresolvedDomain domain.example
```

### 7.6 Только A-записи

Текущие скрипты используют только записи типа `A`, то есть IPv4.

Для MVP приложение должно повторять это поведение.

Позже можно добавить опцию:

```text
A only
AAAA only
A + AAAA
```

Но экспорт hosts для совместимости с текущими файлами должен использовать `A only`.

### 7.7 Порядок IP

Текущий скрипт берёт первый IP из ответа DNS:

```powershell
$firstIp = [string]$primaryRecords[0].IPAddress
```

В приложении режим совместимости должен делать то же самое.

Важно: DNS может возвращать IP в разном порядке. Поэтому результаты могут отличаться между запусками.

---

## 8. Формат результата `.txt`

Выходной файл должен быть обычным текстовым hosts-like файлом.

### 8.1 Активная hosts-запись

```text
<ip> <domain>
```

Пример:

```text
80.74.29.235 chatgpt.com
```

### 8.2 Forwarded

```text
#forwarded <domain>
```

Пример:

```text
#forwarded cdn.auth0.com
```

### 8.3 Unresolved

```text
#unresolvedDomain <domain>
```

Пример:

```text
#unresolvedDomain example.invalid
```

### 8.4 Duplicate

```text
#duplicate <domain>
```

Пример:

```text
#duplicate cdn.auth0.com
```

### 8.5 Комментарии из исходного файла

Сохраняются как есть.

Пример:

```text
# --- OpenAI ChatGPT ---
```

### 8.6 Пустые строки

Сохраняются как пустые строки.

### 8.7 Кодировка

Текущие host_out-файлы созданы как UTF-8 with BOM.

Для максимальной совместимости экспорт должен иметь настройку:

```text
UTF-8 with BOM
UTF-8 without BOM
```

Значение по умолчанию для режима совместимости:

```text
UTF-8 with BOM
```

---

## 9. Статистика генерации

После генерации приложение должно показывать статистику, аналогичную PowerShell-скрипту:

```text
Всего строк обработано
Доменов успешно разрешено
Доменов исключено (#forwarded)
Доменов не разрешено (#unresolvedDomain)
Дублей пропущено (#duplicate)
```

Модель:

```kotlin
data class GenerationStats(
    val totalLines: Int,
    val resolvedDomains: Int,
    val forwardedDomains: Int,
    val unresolvedDomains: Int,
    val duplicateDomains: Int
)
```

Счётчики должны соответствовать текущей логике:

- `totalLines` увеличивается на каждую входную строку;
- комментарии и пустые строки не увеличивают domain-счётчики;
- `resolvedDomains` увеличивается только при активной hosts-записи;
- `forwardedDomains` увеличивается только при `#forwarded`;
- `unresolvedDomains` увеличивается только при `#unresolvedDomain`;
- `duplicateDomains` увеличивается только при `#duplicate`.

---

## 10. Модели данных

### 10.1 DNS-пресет

```kotlin
data class DnsProviderPreset(
    val id: String,
    val title: String,
    val primaryDns: String,
    val checkDns: String = "8.8.8.8",
    val outputFileName: String,
    val enabledByDefault: Boolean = true,
    val description: String? = null
)
```

### 10.2 Строка входного файла

```kotlin
sealed interface DomainSourceLine {
    val raw: String

    data class Blank(
        override val raw: String
    ) : DomainSourceLine

    data class Comment(
        override val raw: String
    ) : DomainSourceLine

    data class Domain(
        override val raw: String,
        val domain: String
    ) : DomainSourceLine
}
```

### 10.3 Результирующая строка

```kotlin
sealed interface HostOutputLine {
    fun asText(): String

    data class Blank(val raw: String = "") : HostOutputLine {
        override fun asText(): String = ""
    }

    data class Comment(val text: String) : HostOutputLine {
        override fun asText(): String = text
    }

    data class Resolved(
        val domain: String,
        val ip: String,
        val allPrimaryIps: List<String>,
        val checkIps: List<String>
    ) : HostOutputLine {
        override fun asText(): String = "$ip $domain"
    }

    data class Forwarded(
        val domain: String,
        val primaryIps: List<String>,
        val checkIps: List<String>
    ) : HostOutputLine {
        override fun asText(): String = "#forwarded $domain"
    }

    data class Unresolved(
        val domain: String,
        val reason: String? = null
    ) : HostOutputLine {
        override fun asText(): String = "#unresolvedDomain $domain"
    }

    data class Duplicate(
        val domain: String
    ) : HostOutputLine {
        override fun asText(): String = "#duplicate $domain"
    }
}
```

### 10.4 Полный результат генерации

```kotlin
data class GenerationResult(
    val preset: DnsProviderPreset?,
    val outputLines: List<HostOutputLine>,
    val stats: GenerationStats,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val errors: List<GenerationError>
) {
    val text: String
        get() = outputLines.joinToString(separator = "\n") { it.asText() }
}
```

---

## 11. Парсер `domainlist.txt`

Парсер должен принимать полный текст или список строк и возвращать `List<DomainSourceLine>`.

Правила:

```kotlin
fun parse(lines: List<String>): List<DomainSourceLine> {
    return lines.map { line ->
        when {
            line.isBlank() -> DomainSourceLine.Blank(line)
            line.trimStart().startsWith("#") -> DomainSourceLine.Comment(line)
            else -> DomainSourceLine.Domain(raw = line, domain = line.trim())
        }
    }
}
```

В UI желательно также строить секции:

```kotlin
data class DomainGroup(
    val title: String,
    val lines: List<DomainSourceLine.Domain>
)
```

Секция начинается с комментария формата:

```text
# --- Section Name ---
```

Название секции можно извлекать так:

```text
# --- OpenAI ChatGPT --- -> OpenAI ChatGPT
```

Если домены идут до первой секции, их можно поместить в группу `Без секции`.

---

## 12. DNS resolver

### 12.1 Абстракция

```kotlin
interface DnsResolver {
    suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Long = 5000
    ): DnsResolveResult
}
```

### 12.2 Результат DNS-запроса

```kotlin
sealed interface DnsResolveResult {
    data class Success(
        val ips: List<String>,
        val rawRecords: List<DnsRecord> = emptyList()
    ) : DnsResolveResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : DnsResolveResult
}
```

### 12.3 Требования

Resolver должен уметь делать DNS-запрос именно к указанному DNS-серверу, а не только через системный DNS.

Это важно, потому что вся логика строится на сравнении ответов разных DNS.

### 12.4 JVM/Android реализация

Для MVP допустимо использовать JVM DNS-библиотеку, например `dnsjava`, если она подходит для Android и Desktop.

Требования к реализации:

- поддержка DNS-сервера по hostname и IP;
- поддержка A-записей;
- таймаут;
- корректная обработка ошибок;
- сохранение порядка IP из ответа, если библиотека его возвращает.

### 12.5 iOS реализация

Если iOS не требуется в MVP, можно оставить заглушку или DoH-реализацию позже.

---

## 13. Генератор hosts

Основной use-case:

```kotlin
class HostsGenerator(
    private val dnsResolver: DnsResolver
) {
    suspend fun generate(request: GenerateHostsRequest): GenerationResult
}
```

### 13.1 Требования к генератору

- должен сохранять порядок строк;
- должен сохранять комментарии;
- должен сохранять пустые строки;
- должен поддерживать `dedup`;
- должен отличать `resolved`, `forwarded`, `unresolved`, `duplicate`;
- должен возвращать как текст, так и структурированные данные;
- должен поддерживать прогресс;
- должен поддерживать отмену операции.

### 13.2 Прогресс

```kotlin
data class GenerationProgress(
    val currentLine: Int,
    val totalLines: Int,
    val currentDomain: String?,
    val stats: GenerationStats
)
```

Генератор должен уметь отправлять прогресс через callback или Flow:

```kotlin
fun generateAsFlow(request: GenerateHostsRequest): Flow<GenerationEvent>
```

События:

```kotlin
sealed interface GenerationEvent {
    data class Started(val totalLines: Int) : GenerationEvent
    data class LineProcessing(val lineNumber: Int, val line: String) : GenerationEvent
    data class DomainResolved(val domain: String, val ip: String) : GenerationEvent
    data class DomainForwarded(val domain: String) : GenerationEvent
    data class DomainUnresolved(val domain: String, val reason: String?) : GenerationEvent
    data class Duplicate(val domain: String) : GenerationEvent
    data class Progress(val progress: GenerationProgress) : GenerationEvent
    data class Finished(val result: GenerationResult) : GenerationEvent
}
```

---

## 14. UI KMP Compose

### 14.1 Главный экран

Главный экран должен содержать:

- выбор DNS-пресета;
- поля `Primary DNS` и `Check DNS`;
- переключатель `dedup`;
- кнопку `Сгенерировать`;
- кнопку `Сгенерировать для всех`;
- прогресс;
- вкладки результата;
- кнопки `Скопировать`, `Экспорт TXT`, `Экспорт CSV`.

### 14.2 Экран доменов

Функции:

- просмотр доменов по секциям;
- поиск по домену;
- отображение количества доменов в секции;
- подсветка дублей;
- включение/выключение домена для генерации;
- импорт своего `domainlist.txt`.

### 14.3 Экран DNS-пресетов

Функции:

- список встроенных DNS;
- добавление пользовательского DNS;
- редактирование `primaryDns`;
- редактирование `checkDns`;
- проверка доступности DNS;
- выбор имени выходного файла.

### 14.4 Экран результата

Должен показывать:

- весь итоговый hosts-текст;
- только активные hosts-записи;
- только `#forwarded`;
- только `#unresolvedDomain`;
- только `#duplicate`;
- статистику;
- лог обработки.

### 14.5 Копирование результата

Кнопка `Скопировать` должна копировать полный текст, идентичный экспортируемому файлу.

### 14.6 Экспорт TXT

Кнопка `Экспорт TXT` должна сохранять файл с именем из пресета, например:

```text
host_out_dns.comss.one.txt
```

### 14.7 Экспорт CSV

Если сгенерировано несколько результатов, приложение должно уметь экспортировать сравнительную таблицу.

---

## 15. CSV-сравнение результатов

Аналог `merge_hosts.ps1`.

### 15.1 Вход

Список результатов:

```kotlin
List<GenerationResult>
```

### 15.2 Логика

1. Берём имена провайдеров.
2. Формируем заголовок через `;`.
3. Берём минимальное количество строк среди результатов, если включён режим совместимости со старым скриптом.
4. Для каждой позиции строки добавляем значение из каждого результата.
5. Если значение содержит `;` или `"`, экранируем CSV-кавычками.

### 15.3 Формат

Разделитель:

```text
;
```

Экранирование:

```text
value -> value
hello;world -> "hello;world"
a "quote" -> "a ""quote"""
```

### 15.4 Имя файла

В приложении использовать единое имя:

```text
combined_hosts.csv
```

Можно дать пользователю выбрать другое имя.

---

## 16. Настройки приложения

Минимальные настройки:

- DNS timeout, по умолчанию 5000 мс;
- параллельность DNS-запросов;
- dedup on/off;
- UTF-8 BOM on/off;
- показывать/скрывать forwarded;
- показывать/скрывать unresolved;
- стратегия выбора IP.

Для режима полной совместимости:

```text
dedup = true
record type = A
IP strategy = first
preserve comments = true
preserve blank lines = true
encoding = UTF-8 with BOM
```

---

## 17. Стратегии выбора IP

В MVP обязательна только стратегия:

```text
First
```

Расширения:

```kotlin
enum class IpSelectionStrategy {
    First,
    AllAsSeparateLines,
    Manual,
    Random
}
```

В режиме совместимости использовать только `First`.

---

## 18. Особенности и потенциальные проблемы

### 18.1 DNS round-robin

DNS может возвращать IP в разном порядке. Из-за этого первый IP может меняться.

### 18.2 CDN

Для CDN домен может иметь множество IP. Логика `#forwarded` по одному общему IP может давать ложные исключения.

Это поведение нужно сохранить для совместимости, но в UI можно показывать предупреждение:

```text
Домен помечен forwarded, потому что найден хотя бы один общий IP.
```

### 18.3 Недоступность DNS

Если основной DNS недоступен — домены становятся `#unresolvedDomain`.

Если проверочный DNS недоступен — домены считаются resolved, если основной DNS ответил.

### 18.4 Дубли

Текущая дедупликация точная и чувствительна к строке после `trim()`.

Не нормализуются:

- регистр;
- trailing dot;
- IDN;
- punycode.

### 18.5 Кодировки старых файлов

Часть CMD/PS1 файлов имеет Windows-1251. Новое приложение должно использовать UTF-8.

---

## 19. Тестовые сценарии

### 19.1 Комментарий

Вход:

```text
# --- Test ---
```

Выход:

```text
# --- Test ---
```

### 19.2 Пустая строка

Вход:

```text

```

Выход:

```text

```

### 19.3 Успешный resolved

Primary:

```text
1.1.1.1
```

Check:

```text
8.8.8.8
```

Пересечений нет.

Выход:

```text
1.1.1.1 example.com
```

### 19.4 Forwarded

Primary:

```text
1.1.1.1
2.2.2.2
```

Check:

```text
2.2.2.2
3.3.3.3
```

Выход:

```text
#forwarded example.com
```

### 19.5 Unresolved

Primary не вернул A-записи.

Выход:

```text
#unresolvedDomain example.com
```

### 19.6 Check DNS error

Primary вернул:

```text
1.1.1.1
```

Check DNS упал.

Выход:

```text
1.1.1.1 example.com
```

### 19.7 Duplicate

Вход:

```text
example.com
example.com
```

Primary для первого домена успешный.

Выход:

```text
1.1.1.1 example.com
#duplicate example.com
```

### 19.8 Duplicate после unresolved

Вход:

```text
example.invalid
example.invalid
```

Primary оба раза не вернул A.

Выход в режиме совместимости:

```text
#unresolvedDomain example.invalid
#unresolvedDomain example.invalid
```

А не:

```text
#unresolvedDomain example.invalid
#duplicate example.invalid
```

---

## 20. Проверка результата на текущих данных

Для проверки можно использовать существующие `host_out_*.txt` как reference-файлы.

Текущие известные результаты:

| Файл | hosts-записей | forwarded | unresolved | duplicate |
|---|---:|---:|---:|---:|
| `host_out_dns.astracat.ru.txt` | 200 | 111 | 37 | 1 |
| `host_out_dns.comss.one.txt` | 235 | 78 | 35 | 1 |
| `host_out_dns.geohide.ru.txt` | 288 | 44 | 16 | 1 |
| `host_out_xbox-dns.ru.txt` | 215 | 97 | 36 | 1 |

Важно: полное совпадение строк не всегда гарантировано из-за динамики DNS, CDN и порядка IP.

---

## 21. MVP-объём работ

Первая версия должна уметь:

1. открыть встроенный `domainlist.txt`;
2. показать список секций и доменов;
3. выбрать DNS-пресет;
4. включить/выключить `dedup`;
5. запустить генерацию;
6. показать прогресс;
7. показать итоговый hosts-текст;
8. скопировать итоговый текст;
9. экспортировать `.txt` файл;
10. показать статистику.

---

## 22. Расширенная версия

После MVP добавить:

1. генерацию для всех DNS-пресетов;
2. сравнение результатов;
3. экспорт CSV;
4. пользовательские DNS-пресеты;
5. импорт/экспорт списка доменов;
6. редактирование доменов в UI;
7. IPv6/AAAA;
8. DoH;
9. историю запусков;
10. сохранение настроек.

---

## 23. Предлагаемая структура KMP-проекта

```text
composeApp/
  src/
    commonMain/
      kotlin/
        app/
          App.kt
        domain/
          DnsProviderPreset.kt
          DomainSourceLine.kt
          HostOutputLine.kt
          GenerationResult.kt
          GenerationStats.kt
        parser/
          DomainListParser.kt
        generator/
          HostsGenerator.kt
          ForwardedDetector.kt
        resolver/
          DnsResolver.kt
          DnsResolveResult.kt
        export/
          HostsTextExporter.kt
          CsvComparisonExporter.kt
        ui/
          AppState.kt
          screens/
            MainScreen.kt
            DomainsScreen.kt
            PresetsScreen.kt
            ResultScreen.kt
          components/
            ResultToolbar.kt
            StatsPanel.kt
            LogPanel.kt
    commonTest/
      kotlin/
        DomainListParserTest.kt
        HostsGeneratorTest.kt
        CsvComparisonExporterTest.kt
    androidMain/
      kotlin/
        resolver/
          AndroidDnsResolver.kt
    desktopMain/
      kotlin/
        resolver/
          DesktopDnsResolver.kt
```

---

## 24. Критерии приёмки

Приложение считается выполненным, если:

1. Оно запускается как Compose приложение.
2. В нём есть встроенный список доменов из `domainlist.txt` или возможность его импортировать.
3. Есть DNS-пресеты из текущих `start_*.cmd`.
4. Генератор повторяет логику `dns-sni-proxy-hosts-generator.ps1`.
5. Для каждого домена выводится один из результатов:
   - active hosts line;
   - `#forwarded`;
   - `#unresolvedDomain`;
   - `#duplicate`.
6. Комментарии и пустые строки сохраняются.
7. Итоговый текст можно скопировать.
8. Итоговый текст можно экспортировать в файл вида `host_out_dns.comss.one.txt`.
9. Отображается статистика генерации.
10. Есть обработка ошибок DNS и отмена генерации.

---

## 25. Главное итоговое требование

Пользователь должен получить результат, аналогичный текущим файлам:

```text
host_out_dns.comss.one.txt
host_out_dns.geohide.ru.txt
host_out_xbox-dns.ru.txt
```

То есть приложение должно формировать обычный текстовый список, который можно:

- открыть;
- скопировать;
- вставить в другой файл;
- сохранить как `.txt`;
- использовать для SNI Proxy/hosts-подобной конфигурации.
