# 09. Бэклог (эпики → задачи)
Тип: design/android/backend/legal/analytics/store/QA. Приоритет P0/P1/P2. Оценка S/M/L.

## E1. Проект и инфраструктура
| ID | Название | Тип | P | Est | Зав. | Done когда |
|---|---|---|---|---|---|---|
| E1-01 | Репо, CI (build+detekt+test), семвер | android | P0 | S | — | зелёный CI на main |
| E1-02 | gradle-конфиг, зависимости, Hilt, Compose-тема | android | P0 | S | — | сборка ставится |
| E1-03 | Room: 12 сущностей, миграции v1, SQLCipher+Keystore | android | P0 | M | E1-02 | тесты на API24/34 |
| E1-04 | DataStore настройки | android | P0 | S | E1-02 | ключи PRD |
| E1-05 | Обёртка Analytics + белый список событий | analytics | P0 | S | E1-02 | запрещённые поля физически не передаются |
| E1-06 | Краш-репорт без health-контента (sanitize) | android | P1 | S | E1-05 | beforeSend-тест |

## E2. Онбординг
| E2-01 | Онбординг 4 шага + pre-permission экран | android | P0 | M | E1-03 | flow docs/02 §2 |
| E2-02 | OEM-детект и обучающие экраны | android | P1 | M | E2-01 | Xiaomi/Huawei/Samsung тексты+скрины |
| E2-03 | Дизайн-макеты онбординга | design | P0 | M | — | Figma утверждена |

## E3. Подопечный
| E3-01 | Профиль: имя/год/фото/аллергии/врачи (CRUD) | android | P0 | M | E1-03 | FR-01 |
| E3-02 | Экстренная карточка + шаринг PNG/PDF | android | P0 | M | E3-01 | FR-02 |
| E3-03 | Мульти-подопечный + переключатель чипом (P) | android | P1 | S | E3-01 | FR-03+лимит free |

## E4. Лекарства
| E4-01 | Форма лекарства + расписание (дни/времена чипы) | android | P0 | M | E1-03 | FR-04/05 |
| E4-02 | Список+архив, счётчик лимита Free | android | P0 | M | E4-01 | FR-06/07 |
| E4-03 | Остатки + напоминание докупки (P) | android | P1 | M | E4-01 | FR-08 |
| E4-04 | Today-экран: слоты дня + действия | android | P0 | M | E4-01 | S1 |

## E5. Напоминания (docs/04)
| E5-01 | ReminderScheduler: слоты+exact+fallback | android | P0 | L | E4-01 | таблица состояний docs/04 |
| E5-02 | Receiver+уведомления с действиями+snooze | android | P0 | M | E5-01 | FR-09/12 |
| E5-03 | Повторы/miss-чек/«Не принимал» с чипами | android | P0 | M | E5-02 | FR-10/11 |
| E5-04 | Boot/time/timezone receivers + ReconcilerWorker | android | P0 | M | E5-01 | reboot-тест |
| E5-05 | Тихие часы | android | P1 | S | E5-02 | FR-13 |

## E6. Дневник
| E6-01 | Запись текст+фото (≤5), лента, сегменты | android | P0 | M | E1-03 | FR-15 |
| E6-02 | Free-окно истории 90 дней + premium-разлок | android | P1 | S | E6-01 | FR-17 |

## E7. Визиты
| E7-01 | Appointment CRUD + напоминания за N | android | P0 | M | E1-03 | FR-16 |

## E8. PDF и пакет
| E8-01 | PdfDocument-движок (текст/таблицы/переносы) | android | P0 | M | — | unit на layout |
| E8-02 | Карта к врачу (Free) | android | P0 | M | E8-01 | FR-18 |
| E8-03 | Расширенный отчёт (% приёма, период) (P) | android | P0 | M | E8-02 | FR-19 |
| E8-04 | Пакет передачи ухода ZIP (P) | android | P1 | M | E8-02 | FR-20/24 |
| E8-05 | Backup/restore JSON+фото через SAF | android | P0 | M | E1-03 | FR-21 round-trip |

## E9. Документы/чек-листы
| E9-01 | DocumentMeta загрузка/просмотр/лимит 10 | android | P1 | M | E1-03 | FR-22 |
| E9-02 | Чек-листы: шаблоны+отметки (free 1, P все) | android | P1 | M | E1-03 | FR-23 |

## E10. Семья (v1)
| E10-01 | Экран «Помощь других» → пакет передачи | android | P1 | S | E8-04 | FR-24 честный текст |

## E11. Paywall
| E11-01 | Триггер-менеджер (5 триггеров, лимит 1/сут) | android | P0 | M | E4-02 | docs/01 §11 |
| E11-02 | UI paywall (3 тарифа, год выделен, честные сноски) | design+android | P0 | M | E11-01 | docs/02 §12 |

## E12. Подписка
| E12-01 | Сверка Pay SDK API, artifact, sandbox | android | P0 | M | — | hello-purchase |
| E12-02 | SubscriptionManager + кэш 72 ч + состояния docs/05 | android | P0 | M | E12-01 | таблица состояний |
| E12-03 | «Моя подписка» + restore + ссылки управления | android | P0 | S | E12-02 | FR-30 |
| E12-04 | Товары в консоли (3 SKU, trial 3 дня, grace 7) | store | P0 | S | монетизация | sandbox-покупки |

## E13. Аналитика
| E13-01 | Все события docs/06 + тест белого списка | analytics | P0 | M | E1-05 | debug-лог чист |

## E14. Приватность
| E14-01 | Экраны «Приватность», дисклеймеры, 103/112 | android | P0 | S | E1-04 | FR-32/33 |
| E14-02 | Юр.документы опубликованы (URL в сборке) | legal | P0 | M | юрист | ссылки работают |

## E15. Публикация RuStore
| E15-01 | ASO-тексты+скриншоты+иконка (docs/11) | store | P0 | M | beta | ассеты финальные |
| E15-02 | Демо-данные кнопкой + комментарий модератору | android+store | P0 | S | beta | docs/07 §6 |
| E15-03 | Подача, ответы модератору | store | P0 | S | всё | статус «опубликовано» |

## E16. Поддержка
| E16-01 | Support-email, шаблоны ответов (docs/07 §8–10) | legal | P1 | S | — | автоответ настроен |
| E16-02 | План ответов на отзывы (SLA 24 ч) | store | P1 | S | — | в проде |
