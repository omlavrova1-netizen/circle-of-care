# 06. Аналитика (AppMetrica) — событийная модель

## 0. Железные правила
1. **Запрещено передавать:** названия лекарств, дозы, диагнозы, тексты дневника, имена подопечных, фото, содержимое документов, точные адреса клиник.
2. Разрешены только: bucket-значения, enum-ы, boolean, id-псевдонимы (UUID устройства из AppMetrica), тарифы/триггеры.
3. Before-send фильтр в обёртке `Analytics`: белый список событий и параметров (код — в `analytics/Analytics.kt`).
4. AppMetricica: preinit до согласия? В MVP согласия на ПДн нет (аналитика — метрики использования; в «Приватности» раскрыто). Отключить сбор geo/ device-identifiers сверх минимума — проверить настройки SDK (docs/00).

## 1. События
| Событие | Когда | Параметры (разрешены) | Запрещено | Зачем |
|---|---|---|---|---|
| app_installed | первый запуск | — | — | база |
| onboarding_started/completed | шаги | completed: meds_added_bucket | имена, лекарства | funnel |
| care_recipient_created | профиль сохранён | has_photo:bool | имя, год | funnel |
| medication_added | сохранение | count_after_bucket, source(onboarding/list) | название, доза, времена | activation |
| reminder_created | расписание | times_per_day_bucket | времена | activation |
| notification_permission_requested/granted/denied | запрос | — | — | риск-метрика ядра |
| exact_alarm_granted/denied | проверка | vendor | — | надёжность |
| oem_battery_instruction_shown | экран | vendor | — | надёжность |
| reminder_fired | показ уведомления | delay_bucket_min | название/доза | надёжность |
| intake_confirmed | тап «Принято» | source, delay_bucket_min | — | retention-ядро |
| intake_missed / intake_skipped | пропуск/отказ | repeat_index / reason_enum | — | качество |
| reminder_snoozed | «Отложить» | count_bucket | — | UX |
| diary_entry_created | запись | type_enum, has_photo | текст | retention |
| appointment_added | визит | lead_bucket | врач/клиника | retention |
| pdf_export_started/completed/failed | экспорт | kind(basic/extended/package) | содержимое | aha/value |
| emergency_card_shared | шаринг | — | содержимое | ценность |
| backup_exported/imported | backup | — | файл | доверие |
| family_invite_created | пакет передачи | channel enum | получатель | v1-семья |
| paywall_shown | показ | trigger enum, is_first | — | монетизация |
| trial_started / purchase_started / purchase_success / purchase_failed | оплата | sku | — | монетизация |
| subscription_cancelled / subscription_restored / premium_active / premium_expired | статус | from,to enum | — | retention денег |
| winback_offer_shown/opened | экран отмены | — | — | возвраты |

## 2. Воронки
1. **Активация:** app_installed → onboarding_completed → medication_added → reminder_created → notification_permission_granted → первый intake_confirmed (D1). Цель: ≥50% установок доходят до первого подтверждения.
2. **Монетизация:** paywall_shown → trial_started → purchase_success. Цели: 12% и 35%.
3. **Удержание оплативших:** purchase_success → D7/D30 с ≥3 intake_confirmed/нед.
4. **Отток и возврат:** subscription_cancelled → winback_offer_opened → purchase_success (цель 8%).
5. **Надёжность (не маркетинг, продукт):** notification_permission_granted ≥70%; доля intake_confirmed с delay ≤10 мин ≥85%; доля устройств с exact_alarm_denied — чекать по вендорам.

## 3. Дашборды
Daily: установки, D1/D7 retention, permission rate, подтверждения/юзер, paywall show/trial/paid, выручка (из консоли RuStore, не из аналитики). Недельный: LTV-когорты, каналы (UTM в URL посевов → параметр install_source только из собственных ссылок).
