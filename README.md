# DnsHostsGenerator

Kotlin Multiplatform приложение для диагностики DNS-резолвинга, управления группами доменов, генерации `hosts`-файлов через выбранные DNS-резолверы и импорта DNS Rewrite-записей в NextDNS.

## Назначение

При разработке, тестировании и настройке сети может потребоваться сравнивать ответы разных DNS-резолверов, поддерживать большие списки доменов и формировать воспроизводимые `hosts`-конфигурации.

DnsHostsGenerator автоматизирует этот процесс:

* хранит домены по группам;
* позволяет создавать и редактировать пользовательские группы;
* валидирует доменные имена;
* выполняет DNS-запросы через выбранные резолверы;
* сравнивает результаты нескольких DNS-провайдеров;
* генерирует `hosts`-файл;
* показывает статистику по resolved, forwarded, unresolved и duplicate;
* поддерживает импорт DNS Rewrite-записей в NextDNS.

## Как работает DNS-резолвинг

Запросы к выбранному DNS-провайдеру выполняются по DNS-over-TLS (DoT, порт 853) с проверкой TLS-сертификата.

Использование DoT уменьшает влияние локального перехвата DNS-запросов UDP/TCP 53, DNS-прокси и сетевых фильтров на результаты сравнения резолверов.

* если DoT недоступен, используется fallback на обычный DNS через UDP/TCP 53;
* контрольный DNS `8.8.8.8` запрашивается через DoT endpoint `dns.google:853`;
* результат выбранного резолвера сравнивается с результатом контрольного резолвера;
* если оба резолвера возвращают одинаковый IP, домен помечается `#forwarded`: дополнительная запись в `hosts` для него не требуется;
* большое количество одинаковых результатов может указывать на особенности сетевой конфигурации или DNS-перехват.

## Scope

DnsHostsGenerator является инструментом DNS-диагностики и управления `hosts`-конфигурациями.

Приложение не реализует VPN, TUN-интерфейс, SOCKS/HTTP-прокси, транспортный туннель, перенаправление IP-трафика или сервис анонимизации.

Пользователь самостоятельно определяет допустимость использования созданных конфигураций в соответствии с применимым законодательством, правилами сети и условиями сторонних сервисов.

## Основные сценарии

### Генерация hosts

1. Открыть приложение.
2. Включить нужные группы доменов.
3. Оставить рекомендуемый DNS или выбрать DNS-пресеты для сравнения.
4. Нажать **Сгенерировать hosts**.
5. Скопировать или сохранить результат.

### Импорт в NextDNS

1. Перейти на экран **NextDNS Import**.
2. Вставить список доменов или hosts-формат.
3. Нажать **Парсить** для проверки и подготовки rewrite-записей.
4. Нажать **Импортировать в NextDNS**.
5. Дождаться создания временного профиля и импорта доменов.
6. Скопировать полученный **Private DNS** кнопкой **Скопировать DNS сервер**.

Поддерживается ввод:

```text
example.com
another-domain.org
```

и hosts-формат:

```text
1.2.3.4 example.com
2001:4860:4860::8888 ipv6-example.org
```

Для обычного списка доменов используется IP из поля **IP для списка доменов**. Для hosts-строк IP берётся из самой строки.

## Возможности NextDNS импорта

- создание нового временного NextDNS аккаунта/профиля для импорта;
- импорт доменов в NextDNS Rewrites;
- автоматический разбор plain domain list и hosts-формата;
- поддержка IPv4, IPv6 и IDN/punycode доменов;
- пропуск loopback-адресов;
- поиск дублей, конфликтов и ошибок парсинга;
- пропуск уже существующих rewrite-записей;
- повторы запросов при `429` и временных `5xx` ошибках;
- проверка результата после импорта;
- отмена активного импорта;
- повтор с новым аккаунтом при ошибке;
- подробный прогресс: режим скорости, всплески, ожидание между повторами, прошло/осталось, скорость, успешные/ошибки/повторы;
- цветовые состояния прогресса для обработки, ожидания, повторов и проверки;
- копирование DNS-сервера профиля в буфер обмена.

## Данные доменов

Приложение использует Room KMP database.

При первом запуске база заполняется seed-данными из:

```text
shared/src/commonMain/composeResources/files/seed_data.sql
```

В базе есть:

- группы доменов;
- домены внутри групп;
- флаг `isEnabled` для группы;
- пользовательские домены с `isCustom`.

Текстовая вкладка оставлена для просмотра итогового списка в старом формате:

```text
# --- Group name ---
domain1.com
domain2.com
```

## Модули

- `shared` — общая логика, UI на Compose Multiplatform, Room database, генератор hosts, NextDNS клиент и импорт.
- `androidApp` — Android-приложение.
- `desktopApp` — Desktop/JVM-приложение.

## Сборка

Android debug APK:

```bash
./gradlew :androidApp:assembleDebug
```

Desktop/JVM:

```bash
./gradlew :desktopApp:assemble
```

Desktop portable distribution:

```bash
./gradlew :desktopApp:createDistributable
```

MSI installer:

```bash
./gradlew :desktopApp:packageMsi
```

Проверка shared-модуля:

```bash
./gradlew :shared:assemble
```

Полная быстрая проверка:

```bash
./gradlew :shared:assemble :androidApp:assembleDebug :desktopApp:assemble
```

## Технологии

- Kotlin Multiplatform
- Compose Multiplatform
- Android Gradle Plugin 9
- Room KMP
- Koin
- Navigation Compose
- OkHttp
- kotlinx.serialization
- dnsjava
- DNS-over-TLS (DoT)

## Disclaimer

DnsHostsGenerator is a DNS diagnostics and hosts-file generation utility.

The project does not provide VPN, tunneling, proxy relay, anonymization, or IP traffic routing services.

Users are responsible for ensuring that their use of DNS, hosts configurations, third-party resolvers and external services complies with applicable laws, network policies and third-party terms of service.
