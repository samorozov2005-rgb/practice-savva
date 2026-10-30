# Финальный аудит требований

Дата повторной локальной проверки: 28.10.2026 (Europe/Moscow), macOS 26.5 ARM64. Статусы: PASS — реализовано и проверено; IMPLEMENTED — реализовано, но эта проверка не выполнялась; BLOCKED — нужна внешняя инфраструктура; FAIL — не выполнено.

| Требование | Статус | Реализация / тест / ручная проверка |
|---|---|---|
| Java 21 + Spring Boot + MVC/JPA/Validation | PASS | `build.gradle.kts`, `SupportTicketApplication`; `./gradlew check` |
| Реальный Kotlin в приложении | PASS | `KotlinReportingService`, `SummaryReport`; 4 Kotlin-теста, включая Java provider |
| PostgreSQL/Flyway/validate | PASS | V1/V2, `application.yml`; Testcontainers + Compose, actuator показал 2 миграции |
| Ticket/Category/History, FK/check/index | PASS | domain и V1; schema validation в integration/Compose |
| Демонстрационные категории | PASS | V2; `flywaySeedsCategories` и контейнерный GET |
| POST/GET/PATCH ticket | PASS | controller/service; unit, integration, E2E A |
| Граф статусов/терминальность/closedAt | PASS | `TicketService.allowed`; unit + оба E2E |
| Атомарные status + history | PASS | `@Transactional`; DB trigger заставляет history insert упасть, `historyInsertFailureRollsBackTicketStatusInPostgres` подтверждает status NEW и history 0 после rollback |
| Актуальная `version` в response | PASS | explicit flush; `updateAndStatusResponsesContainFlushedVersions` проверяет 0→1→2 и последующий GET |
| Защита от потерянных обновлений/HTTP 409 | PASS | `Ticket.@Version`; `staleConcurrentUpdateIsRejected`, `optimisticLockingFailureIsReturnedAsConflict` |
| История и порядок | PASS | repository order; `statusHistoryIsOrderedAndPersisted` |
| Фильтры/поиск/пагинация/сортировка | PASS | Specifications, max size 100, whitelist; integration combined filter |
| Категории | PASS | controller/service; integration и container request |
| Просрочка и граничный момент | PASS | Clock + strict `isBefore`; 2 mapper tests |
| Единые 400/404/409/500 | PASS | `GlobalExceptionHandler`; validation/not-found/conflict tests |
| OpenAPI/Swagger и HTTP-примеры | PASS | config/annotations/`requests.http`; Swagger вернул HTTP 302 на UI redirect |
| Отчёт `[from,to)` и все метрики | PASS | Kotlin service; Kotlin tests + E2E A |
| Минимум 10 unit / 6 integration / 2 E2E | PASS | 15 Java unit + 4 Kotlin + 12 PostgreSQL integration, два `@Tag("e2e")`; 31/31 |
| Управляемый Clock/изоляция/no sleep | PASS | fixed Clock и cleanup; inspection + tests |
| Покрытие Java/Kotlin | PASS | JaCoCo: instruction 93.0%, branch 76.7%, line 95.9%; verification passed |
| Статический анализ/форматирование | PASS | Spotless Java/Kotlin; `spotlessCheck` passed |
| Multi-stage Docker/non-root/health | PASS | Dockerfile; два ARM64 local tags собраны, readiness проверен |
| Linux AMD64 переносимость image | PASS | `docker buildx build --platform linux/amd64 --load`; inspect показал `amd64/linux` (Windows/WSL2 runtime остаётся BLOCKED) |
| Compose DB+app, stable project/volume/readiness | PASS | `name`, `COMPOSE_PROJECT_NAME`; изолированный `practice-savva-finalization`, оба containers healthy |
| Первый/повторный запуск и persistence | PASS | ticket id 1 и history пережили app restart, DB restart, v1→v2 и rollback |
| Логи и восстановление DB-соединения | PASS | `docker compose logs`; readiness снова 200 после restart DB |
| Backup/restore/check | PASS | `.env`-согласованная container config; restore отдельно; source/restore counts categories/tickets/history = 4/1/1; primary неизменна |
| Локальный deploy/rollback | PASS | v1 first/same, v2, manual rollback, failed readiness auto-rollback, failed rollback recovery, invalid/missing image, Docker unavailable, no previous, lock, interruption |
| Persistent deploy directory/state | PASS | `DEPLOY_DIR`, atomic current/previous/pending, stable Compose project, `--no-build`; локально проверено |
| GitHub Actions verify/publish/deploy | BLOCKED | workflow создан; нужны repository, GHCR, protected environment и runner студента |
| Workflow YAML/static analysis | PASS | Ruby YAML parser и containerized `actionlint` с custom runner label — без ошибок |
| PR не запускается self-hosted | IMPLEMENTED | workflow направляет PR только в `verify`; фактическая проверка требует GitHub |
| workflow_dispatch SHA contract | IMPLEMENTED | 40 hex SHA валидируется, owner lower-case, полная GHCR ссылка формируется безопасно; GitHub run BLOCKED |
| macOS/Windows/WSL2 инструкции | PASS | README и `docs/defense/06-student-setup.md` просмотрены на соответствие файлам |
| Requirements, backlog, оценки | PASS | `docs/requirements.md` |
| ER/process/architecture/CI diagrams | PASS | Mermaid в `docs/architecture.md` |
| Test plan/operations/report | PASS | соответствующие файлы `docs/` |
| Defect log без вымысла | PASS | `docs/defects.md`; DEF-002 version/flush и DEF-003 deploy state реально выявлены и исправлены |
| Защита: 6 документов и ≥35 вопросов | PASS | `docs/defense/`; 50 вопросов |
| Реальный remote deployment | BLOCKED | намеренно не выполнялся: нет Git/GitHub/runner/стенда |

## Фактические команды и результаты

- `./gradlew --version` — Gradle 8.14.3, JVM 21.0.9, macOS ARM64.
- `./gradlew spotlessCheck check` — BUILD SUCCESSFUL, 31 тест, 0 failures/errors; PostgreSQL Testcontainers и coverage verification.
- `docker build -t ...:local-v1 .` и `...:local-v2` — успешно; `linux/amd64` Buildx build также успешен.
- Изолированный Compose: db/app healthy; readiness `UP`; Flyway 2; Swagger 302 redirect; REST и Kotlin report успешны.
- Persistence: заявка/status/history сохранились после app/DB restarts и image switching.
- Backup/restore: dump создан, отдельная БД проверена, counts `4,1,1`; restore over primary отклонён exit 2.
- Deploy/rollback: v1/v2/manual/automatic/negative/interruption сценарии завершились с ожидаемыми images/state/exit codes.
- GitHub workflow: YAML parsed и `actionlint` прошёл; реальный GitHub execution не выполнялся.

Невыполненных локальных функциональных требований нет. Запуск на Windows x86-64, GHCR publication, protected GitHub pipeline и self-hosted deploy честно остаются BLOCKED до передачи студенту.
