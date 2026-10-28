# Cloudflare Gateway (Zero Trust) — разведка как бесплатная замена NextDNS

> Итог последующей проверки: активация Zero Trust Free требует платёжного способа
> и согласия на возможные списания. Для пользователя это блокер; персональные Override
> не проверены. Ниже — первоначальная разведка, не подтверждение готового решения.
> Актуальный итог: [DNS_ALTERNATIVES_VERIFIED.md](DNS_ALTERNATIVES_VERIFIED.md).

Дата: 28.10.2026. Источники: docs.cloudflare.com (md-версии страниц), OpenAPI-страница API, живые
запросы с этой сети.

## 1. Доступность с этой сети (проверено)

| Хост | Результат |
|---|---|
| `api.cloudflare.com` | HTTP 400 на неизвестный путь — **сервер отвечает, TLS не режется** |
| `dash.cloudflare.com` | HTTP 403 — доступен |
| `api.controld.com` | 400 — доступен, но нужен платный Full Control ($60/год, подтверждено экраном оплаты) |
| `api.nextdns.io` | недоступен (TLS reset/timeout) |

## 2. Что умеет Gateway DNS

- DNS-политики с действиями: **Allow**, **Block**, **Override** (и `safelist`-варианты).
  API-значение действия: `override`.
- **Override** — это и есть аналог hosts/NextDNS-rewrites: политика возвращает заданный адрес
  вместо обычного ответа. В схеме API есть поля `override_ips`, `override_host`, `override_codes`.
- Селекторы (из документации):
  - **Domain**: `any(dns.domains[*] == "example.com")` — **совпадает и с поддоменами**;
  - **Host**: `dns.fqdn == "example.com"` — только точное имя;
  - Location / DoH Subdomain — привязка к конкретной точке.
- Override нельзя комбинировать с селекторами, которые вычисляются **после** резолва
  (Resolved IP и подобные) — только «до».
- Эндпоинт правил: `POST /accounts/{account_id}/gateway/rules` с `Authorization: Bearer <API token>`
  (подтверждено примерами curl в документации).
- **Resolver policies** (свой upstream-резолвер) — только Enterprise, нам не нужны.

## 3. Как это применить на телефоне

Как и у Control D: создаётся DNS-локация (Location), для неё выдаются уникальные адреса:
DoH `https://<location>.cloudflare-gateway.com/dns-query`, DoT `<location>.cloudflare-gateway.com`.
Android «Частный DNS» принимает как раз hostname DoT — то есть на телефоне это работает без root
и без VPN-клиента.

## 4. Что даёт выигрыш по сравнению с hosts

| | hosts | Gateway Override |
|---|---|---|
| Поддомены | только перечисленные имена | селектор **Domain** покрывает поддомены |
| Применение | нужен root или стороннее приложение | Private DNS (штатно) |
| Обновление | вручную | через API из приложения |

## 5. План реализации (если free-тариф подтвердится)

1. `CloudflareClient`: `GET /accounts` (получить `account_id` по токену) →
   `POST /accounts/{id}/gateway/rules` (пачки) → `GET/DELETE /gateway/rules/{rule_id}`.
2. Форма правила для нашей задачи:
   ```json
   {
     "name": "hosts 103.137.248.179 (n доменов)",
     "action": "override",
     "filters": ["dns"],
     "traffic": "any(dns.domains[*] in {\"chatgpt.com\" \"api.openai.com\"})",
     "settings": { "override_ips": ["103.137.248.179"] }
   }
   ```
   Ограничение длины выражения неизвестно → резать по 30–50 доменов на правило.
3. Режимы: «добавить» и «заменить» (список + удаление по `id`).
4. UI: тот же экран импорта, что был для Control D, но без платной пометки; поле токена +
   выбор аккаунта/локации.
5. Токен: шаблон прав `Account → Zero Trust: Edit`, `Account Settings: Read`.

## 6. Что нужно проверить на живом аккаунте (единственная неопределённость)

- **Доступность правила `override` на Free-плане.** В доках действия не помечены планом,
  Zero Trust традиционно бесплатен до 50 пользователей, но подтверждения для Override на free
  найти не удалось — на странице тарифов этого нет.
- Лимит на длину `traffic`-выражения и число правил.
- Что Location действительно раздаёт DoT-hostname (в доках описаны DoH-эндпоинты).

Для проверки нужен: Cloudflare-аккаунт, `API token` (Zero Trust: Edit) — я сделаю read-only
`GET /accounts` и попытку создать одно правило на тестовом домене, потом удалю его.

## 7. Итог

- Control D — рабочий вариант, но **платный** (Full Control, $60/год).
- Cloudflare Gateway — функционально то же (override IP + покрытие поддоменов + Private DNS),
  API с этой сети доступен; остаётся подтвердить бесплатность Override на практике.
- Самый дешёвый и гарантированный путь без аккаунтов — экспорт/шаринг hosts в локальные
  приложения (AdAway, personalDNSfilter), это можно сделать независимо от исхода разведки.
