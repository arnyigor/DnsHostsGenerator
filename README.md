# DnsHostsGenerator

Kotlin Multiplatform приложение для генерации `hosts`-файлов из групп доменов через выбранные DNS-провайдеры.

## Какую боль решает

Когда сервисы частично недоступны, часто приходится вручную собирать домены, проверять их через рабочий DNS, удалять дубли и складывать результат в `hosts`. Это долго, легко ошибиться и неудобно поддерживать.

DnsHostsGenerator делает этот сценарий быстрым:

- хранит домены по группам: OpenAI, Gemini, GitHub, Spotify и т.д.;
- позволяет включать/выключать группы галочками;
- даёт добавлять, редактировать и удалять свои группы и домены;
- валидирует домены при вводе;
- генерирует готовый `hosts`-файл через рекомендуемый DNS;
- может сравнить результат через несколько DNS-пресетов;
- показывает статистику по строкам, активным hosts, unresolved и duplicate.

## Основной сценарий

1. Открыть приложение.
2. Включить нужные группы доменов.
3. Оставить рекомендуемый DNS или выбрать DNS-пресеты для сравнения.
4. Нажать **Сгенерировать hosts**.
5. Скопировать или сохранить результат.

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

- `shared` — общая логика, UI на Compose Multiplatform, Room database, генератор hosts.
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
- dnsjava
