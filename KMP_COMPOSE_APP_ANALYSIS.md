# Анализ текущих файлов и план KMP Compose приложения

## 1. Что сейчас находится в проекте

Проект сейчас представляет собой набор Windows-скриптов для генерации `hosts`-файлов под SNI Proxy на основе списка доменов.

### Основные файлы

- `domainlist.txt` — исходный список доменов, разбитый на тематические секции комментариями.
- `dns-sni-proxy-hosts-generator.ps1` — основной PowerShell-скрипт генерации hosts-файла.
- `dns-sni-proxy-hosts-generator.cmd` — Windows CMD-обёртка над PowerShell-скриптом.
- `start_*.cmd` — готовые пресеты запуска для разных DNS-провайдеров.
- `start_ALL.cmd` — запускает все пресеты в отдельных окнах.
- `merge_hosts.ps1` — объединяет несколько сгенерированных hosts-файлов в CSV для сравнения.
- `merge_hosts.cmd` — CMD-обёртка для `merge_hosts.ps1`.
- `host_out_*.txt` — уже сгенерированные результаты для части DNS-провайдеров.

## 2. Анализ `domainlist.txt`

Текущий файл содержит:

- всего строк: `408`;
- доменных строк: `349`;
- комментариев: `30`;
- пустых строк: `29`;
- дублей: `1` — `cdn.auth0.com` встречается в секциях OpenAI и Google Gemini.

Секции:

- OpenAI ChatGPT;
- Google Gemini AI;
- Microsoft Copilot Xbox;
- GitHub;
- Anthropic Claude;
- X.ai Grok;
- ElevenLabs;
- Codeium;
- DeepL;
- Trae.ai;
- Supercell Games;
- Epic Games;
- JetBrains;
- Linear.app;
- Tidal;
- Spotify;
- Deezer;
- Fitbit Google Fit;
- 4pda;
- Weather.com;
- Weather;
- Twitch;
- TikTok;
- Badoo;
- Broadcom;
- Canva;
- Chess;
- FMHY;
- Patreon;
- Other.

Для KMP-приложения этот файл лучше превратить в импортируемую модель:

```kotlin
data class DomainGroup(
    val name: String,
    val entries: List<DomainEntry>
)

data class DomainEntry(
    val value: String,
    val enabled: Boolean = true,
    val sourceLine: Int? = null
)
```

## 3. Анализ основного генератора

Файл: `dns-sni-proxy-hosts-generator.ps1`.

### Параметры

```powershell
param(
    [string]$primaryDns,
    [string]$checkDns,
    [string]$domainsFile,
    [string]$outputFile,
    [int]$dedup = 0
)
```

Назначение параметров:

- `primaryDns` — DNS, через который нужно получить IP для hosts;
- `checkDns` — контрольный DNS, обычно публичный DNS вроде `8.8.8.8`;
- `domainsFile` — входной список доменов;
- `outputFile` — выходной hosts-файл;
- `dedup` — режим пропуска повторов.

### Алгоритм

Для каждой строки входного файла:

1. Если строка пустая — переносится в результат как пустая строка.
2. Если строка начинается с `#` — переносится как комментарий.
3. Иначе строка считается доменом.
4. При включённом `dedup` повторный домен помечается как:

```text
#duplicate domain.example
```

5. Выполняется DNS-запрос к `primaryDns`.
6. Из ответа берутся только `A`-записи, то есть IPv4.
7. Если основной DNS не вернул IPv4, домен помечается как:

```text
#unresolvedDomain domain.example
```

8. Выполняется DNS-запрос к `checkDns`.
9. Если есть пересечение IP между основным DNS и проверочным DNS, домен считается обычным пробросом/forwarded и помечается как:

```text
#forwarded domain.example
```

10. Если пересечения нет, в hosts-файл попадает первый IPv4 от основного DNS:

```text
1.2.3.4 domain.example
```

### Счётчики

Скрипт считает:

- всего обработанных строк;
- успешно разрешённых доменов;
- исключённых `#forwarded`;
- неразрешённых `#unresolvedDomain`;
- дублей `#duplicate`, если включён `dedup`.

## 4. Текущие результаты генерации

В каталоге сейчас есть 4 сгенерированных файла:

| Файл | hosts-записей | forwarded | unresolved | duplicate |
|---|---:|---:|---:|---:|
| `host_out_dns.astracat.ru.txt` | 200 | 111 | 37 | 1 |
| `host_out_dns.comss.one.txt` | 235 | 78 | 35 | 1 |
| `host_out_dns.geohide.ru.txt` | 288 | 44 | 16 | 1 |
| `host_out_xbox-dns.ru.txt` | 215 | 97 | 36 | 1 |

`merge_hosts.ps1` ожидает 8 файлов, но сейчас отсутствуют результаты для:

- `host_out_dns.mafioznik.xyz.txt`;
- `host_out_dns.malw.link.txt`;
- `host_out_free.shecan.ir.txt`;
- `host_out_pro.shecan.ir.txt`.

Из-за этого объединение CSV сейчас завершится ошибкой, пока эти файлы не будут сгенерированы.

## 5. Анализ CMD-пресетов

`start_*.cmd` задают пары:

```text
primary DNS + check DNS + domainlist.txt + output file + dedup
```

Используемые DNS-провайдеры:

- `dns.astracat.ru`;
- `dns.comss.one`;
- `dns.geohide.ru`;
- `dns.mafioznik.xyz`;
- `dns.malw.link`;
- `free.shecan.ir`;
- `pro.shecan.ir`;
- `176.99.11.77` для `xbox-dns.ru`.

Для KMP-приложения это лучше хранить как пресеты:

```kotlin
data class DnsProviderPreset(
    val id: String,
    val title: String,
    val primaryDns: String,
    val checkDns: String = "8.8.8.8",
    val outputFileName: String
)
```

## 6. Проблемы и ограничения текущей реализации

### 6.1 Windows-only

Скрипт использует `Resolve-DnsName`, который доступен в Windows PowerShell. Для KMP это не подходит напрямую.

Нужно заменить DNS-логику на кроссплатформенную абстракцию:

```kotlin
interface DnsResolver {
    suspend fun resolveA(domain: String, dnsServer: String): List<String>
}
```

### 6.2 Только IPv4

Сейчас обрабатываются только `A`-записи. `AAAA` не учитываются.

Для приложения стоит явно дать настройку:

- IPv4 only;
- IPv6 only;
- IPv4 + IPv6.

### 6.3 Первый IP может быть нестабильным

Скрипт берёт первый IP из ответа DNS. Для CDN и round-robin DNS порядок может меняться.

В приложении лучше показывать все IP и позволять стратегию выбора:

- первый IP;
- случайный IP;
- все IP отдельными hosts-строками;
- ручной выбор.

### 6.4 Логика `#forwarded` может давать ложные срабатывания

Если у основного и проверочного DNS есть хотя бы один общий IP, домен считается `#forwarded` и не попадает в hosts.

Это удобно как эвристика, но для CDN может быть неточно.

### 6.5 Дедупликация только точная

Сейчас дубли ищутся по точному совпадению строки. Не нормализуются:

- регистр;
- завершающая точка;
- пробелы внутри;
- IDN/punycode.

Для приложения лучше сделать нормализацию домена.

### 6.6 Кодировки

Часть файлов в UTF-8, часть CMD/PS1 — в Windows-1251. В KMP-проекте всё лучше перевести на UTF-8.

### 6.7 Несоответствие имени CSV

`merge_hosts.ps1` создаёт:

```text
combined_hosts.csv
```

А `merge_hosts.cmd` сообщает:

```text
hosts_comparison.csv
```

Это нужно исправить при переносе логики.

## 7. Какой KMP Compose app можно сделать

Рекомендуемый формат: Compose Multiplatform приложение с целями:

- Android;
- Desktop JVM;
- позже iOS, если понадобится.

### Основные экраны

1. **Список доменов**
   - импорт `domainlist.txt`;
   - группировка по секциям;
   - включение/выключение доменов;
   - поиск;
   - подсветка дублей.

2. **DNS-провайдеры**
   - список пресетов из `start_*.cmd`;
   - добавление своего DNS;
   - выбор проверочного DNS.

3. **Генерация**
   - запуск генерации;
   - прогресс по строкам;
   - лог событий;
   - отмена операции.

4. **Результаты**
   - вкладки: resolved / forwarded / unresolved / duplicate;
   - просмотр всех IP;
   - сравнение провайдеров;
   - экспорт hosts-файла.

5. **CSV-сравнение**
   - аналог `merge_hosts.ps1`;
   - таблица по DNS-провайдерам;
   - экспорт CSV.

6. **Настройки**
   - дедупликация;
   - IPv4/IPv6;
   - стратегия выбора IP;
   - таймаут DNS-запроса;
   - параллельность запросов.

## 8. Предлагаемая структура KMP-проекта

```text
composeApp/
  src/
    commonMain/
      kotlin/
        app/
          App.kt
        domain/
          DomainEntry.kt
          DomainGroup.kt
          DnsProviderPreset.kt
          HostGenerationModels.kt
        parser/
          DomainListParser.kt
        generator/
          HostsGenerator.kt
          ForwardedDetector.kt
        resolver/
          DnsResolver.kt
        export/
          HostsExporter.kt
          CsvExporter.kt
        ui/
          screens/
          components/
    androidMain/
      kotlin/
        resolver/AndroidDnsResolver.kt
    desktopMain/
      kotlin/
        resolver/DesktopDnsResolver.kt
```

## 9. Центральная бизнес-логика для переноса

PowerShell-алгоритм можно перенести примерно в такой use-case:

```kotlin
class HostsGenerator(
    private val dnsResolver: DnsResolver
) {
    suspend fun generate(request: GenerateHostsRequest): GenerationResult {
        // 1. пройти по строкам domainlist
        // 2. сохранить комментарии и пустые строки
        // 3. проверить дубли
        // 4. получить A-записи от primary DNS
        // 5. получить A-записи от check DNS
        // 6. сравнить пересечение IP
        // 7. вернуть HostLine.Resolved / Forwarded / Unresolved / Duplicate
    }
}
```

Модели результата:

```kotlin
sealed interface HostLine {
    data class Comment(val text: String) : HostLine
    data object Blank : HostLine
    data class Resolved(val domain: String, val ip: String, val allPrimaryIps: List<String>) : HostLine
    data class Forwarded(val domain: String, val primaryIps: List<String>, val checkIps: List<String>) : HostLine
    data class Unresolved(val domain: String, val reason: String?) : HostLine
    data class Duplicate(val domain: String) : HostLine
}
```

## 10. DNS-реализация в KMP

Самая важная техническая часть — DNS-запросы к конкретному DNS-серверу.

Варианты:

1. **Desktop JVM / Android через JVM-библиотеку**
   - использовать DNS-библиотеку, например dnsjava;
   - удобно для Android и Desktop;
   - не покрывает iOS напрямую.

2. **expect/actual**
   - `commonMain` содержит интерфейс;
   - Android/Desktop имеют одну JVM-реализацию;
   - iOS получает отдельную реализацию через Network.framework или DoH.

3. **DNS-over-HTTPS**
   - если провайдеры поддерживают DoH;
   - проще для KMP через Ktor Client;
   - но текущие DNS-пресеты не все обязательно имеют DoH endpoint.

Практичный первый шаг: Android + Desktop на JVM с общей JVM DNS-реализацией.

## 11. MVP приложения

Минимальная первая версия:

- импортировать `domainlist.txt` из ресурсов или файла;
- показать секции и домены;
- выбрать DNS-пресет;
- запустить генерацию;
- показать результат;
- экспортировать `.txt` hosts-файл;
- экспортировать `.csv` сравнение.

## 12. Рекомендуемые следующие шаги

1. Создать KMP Compose skeleton.
2. Перенести `domainlist.txt` в `composeApp/src/commonMain/composeResources/files/domainlist.txt` или оставить импортом из файловой системы.
3. Описать модели данных в `commonMain`.
4. Реализовать парсер `domainlist.txt`.
5. Реализовать генератор без UI и покрыть тестами на текущих файлах.
6. Добавить JVM DNS resolver.
7. Сделать Compose UI.
8. Добавить экспорт hosts и CSV.
9. Сравнить результаты нового генератора с текущими `host_out_*.txt`.
