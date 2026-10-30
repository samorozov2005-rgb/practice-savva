# Разбор кода

- `build.gradle.kts` — версии, Java/Kotlin toolchains, зависимости, JUnit, JaCoCo и Spotless. На защите показать, что Kotlin компилируется в том же JVM-приложении.
- `SupportTicketApplication.java` — точка входа и bean `Clock`; внедрение времени делает тесты детерминированными.
- `domain/Ticket.java` — JPA entity, `@ManyToOne`, enum как STRING, `@Version`, методы `update/changeStatus`. Сеттеров общего назначения нет.
- `domain/Category.java`, `TicketStatusHistory.java` — справочник и аудит переходов.
- `dto/*` — records запросов/ответов с Bean Validation; внешний контракт отделён от таблиц.
- `repository/*` — Spring Data; `TicketRepository` добавляет Specifications и отчётный запрос `[from,to)`.
- `service/TicketService.java` — `create`, `update`, `changeStatus`, `history`, `list`; здесь граф переходов, транзакции, deadline и whitelist сортировки.
- `service/TicketSpecifications.java` — композиция необязательных фильтров; просрочка использует строгий `<` и только открытые статусы.
- `mapper/TicketMapper.java` — response и вычисляемый overdue через Clock.
- `exception/GlobalExceptionHandler.java` — 400/404/409/500 без SQL/stack trace в JSON; детали неожиданной ошибки остаются только в server log.
- `controller/TicketController.java` — HTTP-коды и endpoints; `ReportController.java` демонстрирует вызов Kotlin из Java.
- `report/ReportingDataProvider.java` и `JpaReportingDataProvider.java` — Java-граница данных для Kotlin.
- `src/main/kotlin/.../KotlinReportingService.kt` — `summary`; `groupingBy`, `mapNotNull`, nullable average; одна выборка для всех метрик.
- `SummaryReport.kt` — Kotlin data class.
- `db/migration/V1...sql` — таблицы, FK, checks и индексы; `V2` — воспроизводимые категории. `application.yml` ставит `ddl-auto=validate`.
- `TicketServiceTest`, `TicketMapperTest`, `KotlinReportingServiceTest` — бизнес-юниты и Java/Kotlin interop.
- `TicketApiIntegrationTest` — настоящий PostgreSQL, Flyway, MockMvc, 9 интеграционных проверок и два E2E.
- `Dockerfile` — build/JRE stages и non-root; `compose.yaml` — readiness, dependency, volume.
- `scripts/*` — backup/restore в отдельную БД, deploy по SHA и rollback.
- `.github/workflows/ci-cd.yml` — verify → publish → deploy; PR заканчивается verify.

Связь для демонстрации: FR-03 в requirements → `TicketService.changeStatus` → `invalidTransitionIsAtomic` → HTTP 409 в Swagger.
