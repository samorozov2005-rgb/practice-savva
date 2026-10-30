# Отчёт о финализации на Mac

Дата: 28.10.2026, Europe/Moscow. Среда: macOS 26.5 (Apple Silicon ARM64), JDK 21.0.9, Gradle Wrapper 8.14.3, Docker Engine 29.0.1, Compose 2.40.3, Buildx 0.29.1. Все Docker-проверки выполнялись в отдельном Compose-проекте `practice-savva-finalization` на портах 18080/15432. Исходный `practice-savva` volume и прежний backup не удалялись.

## Что изменено

| Файл | Проблема | Исправление | Проверка |
|---|---|---|---|
| `scripts/lib/compose-common.sh` | не было единой безопасной Compose/env/state логики | stable project, persistent `DEPLOY_DIR`, Docker checks, atomic state, lock, image/readiness helpers | `bash -n`, first/same/failed/interrupted deploy |
| `scripts/deploy.sh` | regex JSON, premature state, нет lock/robust rollback | `docker inspect`, validate/pull, `--no-build`, pending/current/previous после readiness, auto-restore с exit 1 | v1→v2, failed `postgres` image → v1 restored |
| `scripts/rollback.sh` | pull error скрывался, состояние/health ненадёжны | проверяемый target, readiness, original-image recovery, одноразовый rollback, DB не меняется | v2→v1; failed rollback restored v1, exit 1 |
| `scripts/backup.sh` | shell env мог расходиться с Compose `.env`, partial dump оставался | DB settings внутри container, unique private partial file, non-empty/atomic publish | custom dump создан |
| `scripts/restore.sh` | параметры из shell, недостаточная защита основной БД | container DB user/name, validated isolated target, explicit failure | separate restore; primary target rejected exit 2 |
| `scripts/verify-restore.sh` | проверялись только две count-метрики | availability/tables + categories/tickets/history + source comparison | source/restore `4,1,1` |
| `compose.yaml` | project name зависел от checkout directory | top-level stable `name` | config name и containers `practice-savva-finalization-*` |
| `.env.example` | не хватало project/deploy/restore/health contract | документированы все переменные | использована отдельная `.env` |
| `.github/workflows/ci-cd.yml` | dispatch input назывался image tag, но передавался как full image; не было persistent dir | 40-char SHA validation, safe GHCR ref, `DEPLOY_DIR`, minimal permissions, trusted main/manual deploy | YAML parser + actionlint |
| `.github/actionlint.yaml` | actionlint не знал custom runner label | зарегистрирован `student-stand` | actionlint без замечаний |
| `TicketService.java` | DTO мог видеть старую `@Version` до flush | explicit flush перед mapping update/status | REST version 0→1→2 |
| `TicketApiIntegrationTest.java` | не было реального DB rollback истории и version response | PostgreSQL trigger failure rollback + version integration tests | оба теста passed |
| `GlobalExceptionHandlerTest.java` | HTTP 409 optimistic conflict не был закреплён отдельно | unit test handler | passed |
| `.gitignore`, `.dockerignore` | finalization runtime мог попасть в artifacts/context | исключён временный runtime | Docker context не содержит его |
| `README.md`, `docs/operations.md` | старые deploy/backup команды и неясный DB rollback | актуальный persistent deploy, state, auto/manual rollback, backup/restore | команды сопоставлены с файлами и выполнены |
| `docs/practice-report.md`, `docs/requirements-checklist.md`, `docs/defects.md` | исторические результаты 21.10 и старые статусы | фактические результаты 28.10, DEF-002/003, честные PASS/BLOCKED | сверено после команд |
| `docs/student-handoff.md` | отсутствовал единый следующий этап | GitHub, Windows/WSL2, runner, CI/CD и defense checklist | пути/токены оставлены параметрами |

`Dockerfile`, SQL migrations и публичный REST API не изменялись: аудит не выявил необходимости переписывать их.

## Результаты команд

| Команда / сценарий | Результат | Что проверялось | Обнаруженные ошибки |
|---|---|---|---|
| `./gradlew --version` | Gradle 8.14.3, JVM 21.0.9 | Wrapper/JDK | нет |
| `./gradlew build` | BUILD SUCCESSFUL, executable bootJar создан | полная сборка | нет |
| `./gradlew spotlessCheck check` | BUILD SUCCESSFUL | формат, unit, Testcontainers integration, E2E, coverage gate | нет после исправлений |
| JUnit XML | 31 tests, 31 passed, 0 failures/errors | вся автоматизация | исторические 28 заменены фактом |
| JaCoCo XML | instructions 93.0%, branches 76.7%, lines 95.9% | Java/Kotlin bytecode | нет |
| Testcontainers output | `postgres:17-alpine` стартовал, Flyway/JPA/REST tests passed | реальный PostgreSQL, не H2 | нет |
| `docker build` v1 и v2 | оба ARM64 image созданы | multi-stage Dockerfile | нет |
| `docker buildx build --platform linux/amd64 --load` | SUCCESS, image inspect `amd64/linux` | целевая архитектура WSL2 | запуск на Windows не выполнялся |
| `docker compose config` | valid, stable selected project | Compose interpolation | нет |
| first deploy v1 | readiness success, current=v1, previous absent | первый deploy/state/Flyway | нет |
| Swagger/API/report smoke | readiness UP, Swagger 302, Flyway 2, ticket/history/report 1 | web/backend/Kotlin | нет |
| same-image deploy | exit 0, image/state не ротированы | idempotence | нет |
| deploy v2 | running/current=v2, previous=v1 | image switch/state | нет |
| app и DB restart | ticket 1 IN_PROGRESS/history 1 сохранены | named volume/reconnect | нет |
| backup/restore/verify | dump; isolated restore; source=restore `4,1,1` | pg_dump/pg_restore | primary restore корректно отклонён exit 2 |
| manual rollback | running/current=v1, previous cleared, exit 0 | rollback/readiness/no cycle | данные сохранены |
| bad readiness deploy (`postgres:17-alpine`) | deploy exit 1, auto-rollback v1 healthy | failed update contract | ожидаемый failure |
| failed rollback target | rollback exit 1, исходный v1 восстановлен | rollback failure recovery | ожидаемый failure |
| invalid/missing image | exit 2 / exit 1 | input validation/pull errors | ожидаемые failures |
| Docker unavailable simulation | exit 1 до mutation | daemon/CLI guard | ожидаемый failure |
| previous absent | rollback exit 1 | first-deploy boundary | ожидаемый failure |
| live lock | exit 1 | parallel operation guard | ожидаемый failure |
| SIGTERM interruption | exit 143, current=v1, pending recorded, lock released; redeploy v1 recovered | interruption/state integrity | ожидаемое прерывание |
| Ruby YAML parser | parsed | basic workflow syntax | нет |
| containerized actionlint | final exit 0 | GitHub Actions semantics | сначала обнаружил unknown custom label; добавлен config |
| `bash -n ...` и containerized ShellCheck | exit 0, замечаний нет | shell syntax/static analysis | локальный binary отсутствовал, использован официальный container |

Два local tags содержат один и тот же функциональный код: сценарий доказывает переключение images/state, но не функциональные различия релизов.

После проверки изолированные containers корректно остановлены через `compose stop`; named volume `practice-savva-finalization_postgres_data` сохранён. Временные `.env`, state, log и тестовый dump из `.finalization-runtime` удалены, а существующий пользовательский `backups/support-20261021T155525Z.dump` оставлен без изменений.

## Ограничения и BLOCKED

- GitHub repository студента ещё не создан; реальный Actions workflow не запускался.
- GHCR publication/authentication и package permissions не проверены.
- Protected branch/environment и reviewer ещё не настроены.
- Linux x64 self-hosted runner `student-stand` не установлен и не запускался.
- Windows 11/WSL2/Docker Desktop компьютера студента недоступны. AMD64 image собран, но target runtime не проверен.
- Реальный deploy из GHCR на компьютер студента и rollback между двумя разными commit SHA остаются следующим этапом.
- Application rollback не откатывает Flyway/database; совместимость будущих migrations обязана проверяться для каждой версии.
