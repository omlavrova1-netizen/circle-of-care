# 03. Техническое задание (Android)

## 1. Платформа и зависимости
- **minSdk 24** (Android 7.0, покрытие парка РФ), **targetSdk 34** (фактическое требование консоли — docs/00 #6), compileSdk 35.
- Kotlin 2.x, Jetpack Compose (BOM), Material 3, Navigation Compose, Hilt, Room, WorkManager, DataStore (preferences), kotlinx-serialization (backup JSON), Coil (фото).
- **RuStore Pay SDK** — artifact/API сверить с официальной документацией консоли (docs/00 #5); в коде изолирован интерфейсом `SubscriptionDataSource`, до проверки — fake-реализация.
- **AppMetrica** — обёртка `Analytics` (docs/06).
- Запрещено: FCM/GMS-зависимое ядро, рекламные SDK, любые SDK, читающие health-контент.

## 2. Архитектура
MVVM + Clean (легковесная), одноmodule-приложение до v1.5 (модульность — пакетами):
```
ru.krugzaboty.app
 ├─ ui/ (theme, navigation, onboarding, today, medications, diary, profile, paywall, common)
 ├─ domain/ (usecases, models)
 ├─ data/ (local: Room entities/DAO/db, settings; repository impls; backup; pdf)
 ├─ reminders/ (scheduler, receivers, workers, model)
 ├─ subscription/ (SubscriptionManager, RuStoreDataSource, FakeDataSource)
 ├─ analytics/ (Analytics, Events)
 └─ di/
```
Слои: UI (Compose + ViewModel/StateFlow) → Domain (use-case'ы: `AddMedicationUseCase`, `ConfirmIntakeUseCase`, `BuildCarePackageUseCase`, `EvaluatePaywallUseCase`) → Data (репозитории над Room/DataStore; PDF/backup генераторы; SubscriptionDataSource).
Офлайн-логика: Room — единственный источник истины; все операции локальные; UI переживает отсутствие сети по определению.

## 3. База данных (Room, `krug.db`, SQLCipher — ключ в Android Keystore)
Общие правила: UUID String PK; soft-delete `deletedAt: Long?`; все времена событий — epochMillis UTC + отдельный `localTime` при необходимости; индексы указаны. **В облако в MVP не уходит ничего** (облака нет). Сущности FamilyMember/Invitation — схема под v1.5, в v1 не пишутся.

### CareRecipient
`id:String PK, name:String, birthYear:Int?, photoUri:String?, allergiesText:String?, doctorsJson:String (List<Doctor{name,clinic,phone}>), notes:String?, createdAt:Long, updatedAt:Long, isActive:Boolean=1`
Индекс: нет (мало строк). Локально: всё.

### Medication
`id PK, recipientId FK→CareRecipient ON DELETE CASCADE, name:String, form:MedForm (TABLET/CAPSULE/LIQUID/INJECTION/DROPS/OTHER), doseText:String?, note:String?, colorTag:Int?, createdAt:Long, deletedAt:Long?`
Индексы: recipientId. Free-лимит считается по COUNT(deletedAt IS NULL).

### MedicationSchedule
`id PK, medicationId FK→Medication CASCADE, daysMask:Int (биты Пн–Вс), timesJson:String (List<"HH:mm">, 1–6), startDate:Long, endDate:Long?, active:Boolean=1`
Индекс: medicationId. Генерация срабатываний — по mask+times в локальном часовом поясе устройства (docs/04).

### IntakeLog
`id PK, medicationId FK, scheduleId FK, plannedAt:Long (epoch UTC), status:IntakeStatus (SCHEDULED/SHOWN/SNOOZED/CONFIRMED/MISSED/SKIPPED/CANCELLED), actionAt:Long?, missedReason:MissedReason?, note:String?`
Индексы: (medicationId, plannedAt), (status, plannedAt). Уникальный: (scheduleId, plannedAt).

### DiaryEntry
`id PK, recipientId FK, type:DiaryType (MOOD/SYMPTOM/VISIT/OTHER), text:String, photoPathsJson:String (List, ≤5), createdAt:Long`
Индекс: (recipientId, createdAt). Текст — только локально, никогда в аналитику.

### Appointment
`id PK, recipientId FK, title:String, doctor:String?, clinic:String?, address:String?, startsAt:Long, durationMin:Int?, notes:String?, reminderOffsetsJson:String (List<minutes>), notified:Json`
Индекс: startsAt.

### DocumentMeta
`id PK, recipientId FK, title:String, filePath:String (app-private filesDir), mimeType:String, sizeBytes:Long, category:DocCategory (ANALYSIS/DISCHARGE/CONTRACT/OTHER), createdAt:Long`
Free-лимит ≤10 по COUNT. Файлы — internal storage, не MediaStore.

### FamilyMember (v1.5-схема, v1 не используется)
`id PK, recipientId FK, name:String, role:MemberRole (ORGANIZER/HELPER/VIEWER), invitationCode:String?, isActive:Boolean, createdAt:Long`

### Invitation (v1.5-схема)
`id PK, recipientId FK, code:String UNIQUE, role:MemberRole, createdAt:Long, expiresAt:Long, accepted:Boolean`

### SubscriptionState
`id PK=1 (singleton), status:SubStatus (docs/05), sku:String?, expiresAt:Long?, purchaseTokenHash:String?, lastVerifiedAt:Long, source:VERIFIED/CACHED`
Токен — только хеш. Не синхронизируется.

### AppSettings (DataStore, не Room)
`theme(SYSTEM/LIGHT/DARK), bigText:Boolean, quietHours{start,end,enabled}, oemHelpShownFor:String, onboardingDone:Boolean, demoMode:Boolean, lastPaywallShownAt:Long, trialOfferSeenAt:Long`

### ReminderRule
`id PK, medicationId FK? (null=глобальные), repeatCount:Int=3, repeatIntervalMin:Int=15, snoozeMin:Int=15, missedAfterMin:Int=30, quietGuard:Boolean=1`
Free — глобальные значения по умолчанию; кастомизация — P (FR-10).

## 4. Напоминания — см. docs/04 (полная логика, таблица состояний).
## 5. Подписка — см. docs/05.
## 6. PDF
`android.graphics.pdf.PdfDocument` + Canvas (без внешних либ): макеты CareCardPdf (1 стр.), ExtendedReportPdf (период, % приёма по каждому лекарству, тренды), EmergencyCardPng. Шрифт — системный; переносы считать StaticLayout. P95 генерации < 1,5 с на среднем устройстве; тяжёлые отчёты — на Dispatchers.Default.

## 7. Backup/restore
Экспорт: Room → JSON (kotlinx-serialization) + фото-архив в `getExternalFilesDir`/SAF (ACTION_CREATE_DOCUMENT), всегда бесплатно. Импорт: SAF picker → валидация схемы → транзакционная замена. Версия схемы в файле.

## 8. Безопасность
SQLCipher (ключ в Android Keystore, не экспортируется); опциональный биометрический замок на вход (P-фича позже); `allowBackup=false`, `fullBackupContent` исключает db и photos; фото дневника — internal; скриншот-блокировка опционально (настройка); никаких network-вызовов, кроме Pay SDK/AppMetrica.

## 9. Аналитика — только обезличенные события, docs/06. Краш-репорты — без user content (sentry sample rate, beforeSend-санитайзер вырезает строки из нашего пакета данных).

## 10. Производительность и размер
Холодный старт < 2 с; список приёмов — paging; APK ≤ 25 МБ; baseline profiles позже.

## 11. CI/качество
GitHub Actions (сборка, detekt, unit-тесты Room/use-case); релизный AAB/APK подписанный; семвер + changelog для модератора.
