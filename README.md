# Сервис обработки заявок

Учебный монолит на Java 21 и Kotlin: принимает заявки техподдержки, контролирует допустимые статусы и сроки, хранит историю в PostgreSQL и строит сводный отчёт Kotlin-компонентом.

## Возможности и архитектура

- CRUD-поток заявки: создание, получение и ограниченное редактирование.
- Переходы `NEW → IN_PROGRESS → RESOLVED`, а также отмена из `NEW`/`IN_PROGRESS`.
- Атомарная запись статуса и истории, `@Version` защищает от потерянных обновлений.
- Фильтры, поиск, сортировка, пагинация (до 100 элементов), признак просрочки.
- Отчёт за полуинтервал `[from,to)`; среднее время решения измеряется в секундах.
- Единые JSON-ошибки, OpenAPI/Swagger, Actuator, Flyway.

Поток вызова: HTTP controller → service → repository → PostgreSQL. DTO не раскрывают JPA-сущности. Java-контроллер отчёта вызывает `KotlinReportingService`, который получает единую выборку через Java-интерфейс `ReportingDataProvider`. Диаграммы находятся в [docs/architecture.md](docs/architecture.md).

## Зафиксированный стек

Java 21, Spring Boot 3.5.16, Kotlin 2.1.21, Gradle 8.14.3, PostgreSQL 17, Flyway, Spring Data JPA/Hibernate, springdoc-openapi 2.8.14, JUnit 5, Mockito/MockK, Testcontainers 1.21.3, JaCoCo 0.8.13 и Spotless 7.2.1. Образы `postgres:17-alpine` и Eclipse Temurin 21 являются multi-arch для ARM64/AMD64.

## Самый простой запуск — Docker Compose

Нужен только Docker Desktop с Compose v2.

```bash
cp .env.example .env
# замените POSTGRES_PASSWORD в .env
docker compose up --build -d
docker compose ps
curl http://localhost:8080/actuator/health/readiness
```

Откройте Swagger UI: <http://localhost:8080/swagger-ui.html>. Обычная остановка, сохраняющая volume: `docker compose stop`. Повторный запуск: `docker compose start`. Полное удаление командой `down -v` намеренно не входит в стандартный сценарий.

### macOS (Apple Silicon или Intel)

Установите Docker Desktop, включите его, выполните команды выше из Terminal. Эмуляция архитектуры не требуется. Для локальной разработки без контейнера нужны JDK 21 и запущенный PostgreSQL: `./gradlew bootRun`.

### Windows 10/11

Установите WSL2, Docker Desktop и включите WSL Integration. Клонированную/скопированную папку желательно держать в файловой системе WSL. В Ubuntu выполните `cp .env.example .env`, задайте пароль и `docker compose up --build -d`. JDK, Kotlin и Gradle для контейнерного запуска не нужны. Подробно: [docs/defense/06-student-setup.md](docs/defense/06-student-setup.md).

## API

| Метод | Путь | Назначение |
|---|---|---|
| POST | `/api/tickets` | создать заявку (201) |
| GET/PATCH | `/api/tickets/{id}` | получить/изменить |
| PATCH | `/api/tickets/{id}/status` | изменить статус |
| GET | `/api/tickets/{id}/history` | история по времени и id |
| GET | `/api/tickets` | фильтры `status`, `priority`, `categoryId`, `title`, `overdue`; `page/size/sort` |
| GET | `/api/categories` | категории |
| GET | `/api/reports/summary?from=...&to=...` | отчёт ISO 8601 |

Готовые примеры есть в [requests.http](requests.http). `closedAt` задаётся только при `RESOLVED`; для `NEW`, `IN_PROGRESS` и `CANCELLED` остаётся `null`. Срок, равный текущему моменту, ещё не просрочен.

## Разработка и проверки

```bash
./gradlew spotlessCheck test jacocoTestReport
./gradlew check
open build/reports/tests/test/index.html        # macOS
open build/reports/jacoco/test/html/index.html
```

Интеграционные и E2E-тесты автоматически поднимают настоящий PostgreSQL через Testcontainers, поэтому Docker должен работать. Wrapper загружает согласованный Gradle; системный Gradle не нужен.

## Конфигурация и миграции

Переменные приложения: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`. Compose использует `COMPOSE_PROJECT_NAME`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `APP_PORT` и `APP_IMAGE`. Скрипты дополнительно понимают `DEPLOY_DIR`, `RESTORE_DB` и параметры `HEALTH_*` из `.env.example`.

`COMPOSE_PROJECT_NAME=practice-savva` делает имя проекта и volume стабильными независимо от каталога checkout. Локально `DEPLOY_DIR=.` хранит `.env`, backups и `.deploy` в проекте. На self-hosted runner переменная репозитория `DEPLOY_DIR` должна указывать на постоянный каталог стенда вне временного checkout; в нём хранится непубликуемый `.env` и состояние успешных deployments.

`.env.example` содержит лишь шаблон: рабочий пароль нельзя коммитить. Hibernate использует `ddl-auto=validate`; схему создают `V1` и совместимое добавление справочников `V2`. Старые образы должны оставаться совместимы с новой схемой, чтобы application rollback был возможен.

## Backup и restore

```bash
chmod +x scripts/*.sh
./scripts/backup.sh
./scripts/restore.sh backups/support-YYYYMMDDTHHMMSSZ.dump
./scripts/verify-restore.sh
```

Скрипты берут пользователя и имя основной БД из окружения уже запущенного PostgreSQL container, поэтому значения не расходятся с Compose `.env`. Dump сначала пишется во временный файл и публикуется атомарно. Восстановление идёт только в отдельную БД `support_restore`; попытка указать основную БД отклоняется. Проверка сравнивает counts категорий, заявок и истории в исходной и восстановленной БД. Условия и диагностика описаны в [docs/operations.md](docs/operations.md).

## CI/CD, развёртывание и откат

`.github/workflows/ci-cd.yml` проверяет форматирование, unit/integration/E2E-тесты и покрытие на GitHub-hosted runner; только push в защищённую `main` собирает `linux/amd64,linux/arm64` image с тегом полного commit SHA и публикует его в GHCR. Deploy идёт на Linux self-hosted runner с label `student-stand` и защищённым environment. PR-код туда не попадает. Ручной `workflow_dispatch` принимает ровно 40 hex-символов SHA и сам формирует полное имя GHCR image.

Для постоянного учебного стенда:

```bash
export DEPLOY_DIR=/постоянный/каталог/стенда
mkdir -p "$DEPLOY_DIR"
cp .env.example "$DEPLOY_DIR/.env"  # затем задайте уникальный пароль
./scripts/deploy.sh ghcr.io/OWNER/support-ticket-service:FULL_COMMIT_SHA
./scripts/rollback.sh
```

Deploy принимает ровно одно полное имя local/registry image, проверяет Docker, берёт реальный текущий image через `docker inspect`, запускает Compose с `--no-build` и ждёт `/actuator/health/readiness`. Файлы `current-image`, `previous-image` и временный `pending-image` записываются атомарно в `$DEPLOY_DIR/.deploy`; lock не допускает параллельных deploy/rollback. При failed readiness последняя рабочая версия восстанавливается, но deploy всё равно возвращает ненулевой код. Ручной rollback одноразово возвращает `previous-image`, проверяет readiness и не создаёт бесконечное переключение.

Rollback заменяет только application image. Он не откатывает Flyway и никогда не удаляет PostgreSQL volume. Workflow подготовлен, но его реальная работа требует будущего GitHub-репозитория, GHCR и runner студента и здесь не объявляется проверенной.

## Типовые проблемы

- `connection refused` к БД: `docker compose ps db`, затем `docker compose logs db`.
- приложение unhealthy: `docker compose logs app` и `curl -v localhost:8080/actuator/health`.
- Flyway validation failed: не редактируйте применённую миграцию; добавьте новую версию и проверьте таблицу `flyway_schema_history`.
- занят порт: задайте `APP_PORT=8081` или `POSTGRES_PORT=5433` в `.env`.
- Testcontainers не видит Docker: запустите Docker Desktop и проверьте `docker info`.
- deploy сообщает о lock: убедитесь, что указанный PID уже не работает; stale lock скрипт удалит самостоятельно.
- interrupted deploy: `current-image` остаётся последней подтверждённой версией, а `pending-image` показывает незавершённую попытку; повторите deploy рабочей версии.

Полный тест-план, эксплуатация, отчёт и материалы защиты находятся в `docs/`.
