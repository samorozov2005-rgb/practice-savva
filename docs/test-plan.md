# Тест-план

## Подход и команды

Модульные тесты изолируют service/mapper/report с Mockito и MockK. Интеграционные тесты поднимают PostgreSQL 17 через Testcontainers, прогоняют Flyway и REST через MockMvc. Два теста с тегом `e2e` покрывают полные пользовательские цепочки. Общий запуск: `./gradlew test jacocoTestReport`; статический анализ и порог покрытия: `./gradlew spotlessCheck jacocoTestCoverageVerification`; весь gate: `./gradlew check`.

Отчёты: `build/reports/tests/test/index.html` и `build/reports/jacoco/test/html/index.html`. Данные очищаются перед каждым интеграционным тестом, Clock фиксирован на `2026-01-10T10:00:00Z`, `Thread.sleep` отсутствует.

## Матрица

| Требование | Проверка | Ожидание |
|---|---|---|
| FR-01 | `createsTicketWithNewStatusAndServerTime`, `createsPersistsAndGetsTicket` | 201, NEW, server time, запись в БД |
| FR-01/09 | `rejectsDeadlineAtCurrentMoment`, `returnsStructuredValidationError` | 400 и fieldErrors |
| FR-02 | `updatesOpenTicket`, `rejectsEditingResolvedTicket` | изменение либо 409 |
| FR-03/12 | `allowsNewToInProgressAndWritesHistory`, `resolvesInProgressAndSetsClosedAt`, `invalidTransitionIsAtomic` | граф, closedAt, атомарность |
| FR-03/12 | `historyInsertFailureRollsBackTicketStatusInPostgres` | реальный отказ INSERT history откатывает status в PostgreSQL |
| FR-12 | `updateAndStatusResponsesContainFlushedVersions`, `staleConcurrentUpdateIsRejected`, `optimisticLockingFailureIsReturnedAsConflict` | актуальная version, сохранность winner, HTTP 409 |
| FR-04 | `combinesFiltersPaginationAndSorting`, `rejectsUnsafeSortProperty` | корректная выборка/400 |
| FR-05 | четыре `KotlinReportingServiceTest`, E2E A | нули/null, распределения, среднее, неверный период |
| FR-06 | `statusHistoryIsOrderedAndPersisted`, `rejectsHistoryForUnknownTicket` | порядок и 404 |
| FR-09 | `returnsNotFoundWithoutLeakingInternals` | безопасный JSON 404 |
| FR-10/NFR-02 | `flywaySeedsCategories` | миграции применились, четыре категории |
| FR-11 | два `TicketMapperTest` | строгая граница `< now` |
| E2E-A | `scenarioAFullTicketLifecycleAndReport` | create→get→edit→statuses→history→report |
| E2E-B | `scenarioBTerminalTicketRejectsEditWithoutMutation` | 409, данные/история неизменны |

Дополнительные ручные проверки Compose, persistence, Swagger, backup/restore перечислены в `docs/operations.md` и checklist.
