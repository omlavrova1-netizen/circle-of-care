# 04. Логика напоминаний (ядро продукта)

## 1. Модель
Для каждого активного MedicationSchedule на ближайшие N=7 дней генерируются IntakeLog-«слоты» (plannedAt = дата×время в **локальном часовом поясе устройства**, хранится epoch UTC + zone id). Будильник ставится на каждый будущий слот individually.

## 2. Постановка напоминания
1. `ReminderScheduler.sync(recipientId)` вызывается: при сохранении/правке/удалении лекарства, в онбординге, после перезагрузки, при смене времени/пояса, WorkManager-выравнивателем.
2. Удаляет все `PendingIntent` с action `ru.krugzaboty.REMIND` для затронутых scheduleId.
3. На каждый будущий слот: `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, plannedAt, pi)` c `EXTRA_INTAKE_ID`.
4. Если `!alarmManager.canScheduleExactAlarms()` (Android 12+, SCHEDULE_EXACT_ALARM не дан — у пользователя отказ или OEM-политика): `setWindow()` на plannedAt ±10 мин + индикатор «неточные напоминания» в Today + CTA на OEM-экран (docs/02 §17).
5. Разрешения: SCHEDULE_EXACT_ALARM (нормальный уровень, но для приложений не-будильников требует оправдания в сторе; USE_EXACT_ALARM не используем — только для приложений-часов), POST_NOTIFICATIONS (запрос в рантайме).

## 3. Срабатывание
`ReminderReceiver.onReceive` → читает IntakeLog по id:
- статус уже CONFIRMED/SKIPPED/CANCELLED → не показывать (защита от двойного приёма, FR-12).
- в тихих часах и время перенесено? → показ/перенос по правилу quietGuard.
- Показ уведомления: заголовок «Пора принять: {название} {доза}», действия: **«Принято» / «Не принимал» / «Отложить 15 мин»** (RemoteInput на «Не принимал» — чипы причин). Статус → SHOWN.
- Ставит отложенный «miss-чек» alarm на plannedAt + missedAfterMin (30).

## 4. Повторы и пропуск
- miss-чек сработал, статус SHOWN/SCHEDULED → статус MISSED (при первом) и показ повторного уведомления «Не забудьте: …» (count++, до repeatCount=3, интервал 15; каждый повтор — новый miss-чек).
- Повторные уведомления идут даже после MISSED, пока не подтвердят или не отменят.
- Пользователь отмечает «Не принимал» → SKIPPED + причина (chip), повторы гасятся.
- snooze: «Отложить» → SNOOZED + новый alarm на +15 мин; по истечении snooze — повтор.

## 5. Подтверждение приёма
Notification action «Принято» → receiver → IntakeRepository.confirm(intakeId) → CONFIRMED, cancel всех связанных alarm/уведомлений, инкремент streak-статистики. То же из экрана Today.

## 6. Часовые пояса и время
- plannedAt хранится UTC + `zoneId`. При смене пояса (`ACTION_TIMEZONE_CHANGED`) и смене времени (`ACTION_TIME_CHANGED`, DST) — ресинхронизация всех будущих слотов (пересчёт локальных HH:mm → новые epoch).
- DST-край: HH:mm считается по локальному календарю на день слота.

## 7. Перезагрузка и убой процесса
- `BootReceiver` (BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME/TIMEZONE) → `syncAll()` (WorkManager `expedited`,限 ≤15 мин).
- WorkManager `ReminderReconciler` периодический (каждые 6–12 ч, гибко) + OneTime при старте приложения: пересоздаёт слоты, догоняет пропущенные будильники (если plannedAt < now и SCHEDULED → сразу MISSED-уведомление «пропущено»).
- OEM-выживание: обучающие экраны (docs/02 §17) + в бете матрица Xiaomi/Huawei/Honor/Samsung; в About — «Почему напоминание могло не прийти».

## 8. Edge cases
- Изменили расписание задним числом — история не меняется, будущие слоты пересоздаются.
- Удалили лекарство — cancel alarm, история остаётся (FR-07).
- Выключенный телефон на момент срабатывания — при включении reconciler покажет «пропущено» с возможностью отметить «принято вне приложения».
- 200+ будущих слотов — alarm-лимиты Android: ставим только на 7 дней вперёд, остальное — reconciler.

## 9. Таблица состояний напоминания (IntakeStatus)
| Состояние | Вход | Что видит юзер | Выходы |
|---|---|---|---|
| scheduled | создан слот | карточка «сегодня 09:00» | shown / cancelled (правка/удаление) |
| shown | уведомление показано | баннер «ждём подтверждения» | confirmed / skipped / snoozed / missed |
| snoozed | «Отложить» | «напомню в 09:15» | shown / confirmed |
| confirmed | подтвердил | зелёная галочка, streak | — (терминальное) |
| missed | missedAfter без ответа | жёлтый «пропущено» + повторные пуши | confirmed («принял, забыла отметить») / skipped |
| skipped | «Не принимал»+причина | серый + причина | — |
| cancelled | расписание удалено/изменено | исчезает из «сегодня» | — |
| rescheduled | перенос тихими часами/сменой времени | «перенесено на 10:00» | shown |

## 10. События аналитики (без health-текстов)
reminder_created {times_count_bucket}, reminder_fired {delay_bucket_min}, reminder_snoozed {count}, intake_confirmed {source: notification|screen, delay_bucket_min}, intake_missed {repeat_index}, intake_skipped {reason_enum}, notification_permission_granted/denied, exact_alarm_granted/denied, oem_battery_instruction_shown {vendor}, reminder_permission_banner_shown.
