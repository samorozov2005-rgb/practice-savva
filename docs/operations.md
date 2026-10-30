# Эксплуатация

## Нормальный локальный цикл

`docker compose up --build -d`, затем `docker compose ps`, `curl -fsS localhost:8080/actuator/health/readiness`, `docker compose logs --tail=100 app`. Остановка с сохранением данных: `docker compose stop`; повторный запуск: `docker compose start`. Список миграций доступен в `/actuator/flyway` и таблице `flyway_schema_history`.

Проверка persistence: создайте заявку из `requests.http`, запомните id, выполните `docker compose restart`, дождитесь healthy и повторите GET. Для перезапуска только БД: `docker compose restart db`; приложение с `restart: unless-stopped` восстановит соединения, readiness покажет фактическое состояние.

## PostgreSQL недоступен

1. `docker compose ps db` и `docker compose logs --tail=200 db`.
2. `docker compose exec db sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`. Так команда использует фактическую конфигурацию container, а не неэкспортированные переменные shell.
3. Проверить свободное место `docker system df`, корректность пароля и имя volume `docker volume ls`.
4. После исправления `docker compose restart db app`; не удалять volume.

## Spring Boot не проходит health check

1. `docker compose ps app` и `docker inspect --format '{{json .State.Health}}' <container>`.
2. `docker compose logs --tail=300 app`; локально `curl -v localhost:8080/actuator/health`.
3. Проверить DB URL, порт, память Docker и результат Flyway. Если новый image неисправен — `./scripts/rollback.sh`.

## Ошибка Flyway

1. В логах найти номер миграции; `docker compose exec db psql -U support -d support -c 'TABLE flyway_schema_history;'`.
2. Никогда не менять уже применённый SQL в опубликованной версии. Исправление оформлять новой миграцией `V3__...sql`.
3. Для локальной повреждённой тестовой БД сначала сделать backup. `flyway repair` не выполнять без понимания причины checksum.

## Backup/restore

`./scripts/backup.sh` создаёт custom dump сначала как `.partial`, проверяет непустой результат и публикует уникальное имя без перезаписи. Имя/пользователь БД читаются внутри container. `./scripts/restore.sh FILE` пересоздаёт только отдельную `RESTORE_DB` (`support_restore` по умолчанию) и отказывается работать, если target совпал с основной БД. `./scripts/verify-restore.sh` проверяет доступность трёх таблиц и сравнивает counts категорий, заявок и истории с основной БД.

```bash
./scripts/backup.sh
./scripts/restore.sh backups/support-TIMESTAMP-PID.dump
./scripts/verify-restore.sh
```

Для рабочей катастрофы сначала остановить app, сохранить текущую БД и отдельно согласовать восстановление. Предоставленный сценарий намеренно никогда не перезаписывает production.

## Deploy/rollback

### Постоянная конфигурация стенда

Задайте repository variable GitHub `DEPLOY_DIR`: абсолютный постоянный каталог внутри Ubuntu/WSL2. В нём вручную создайте `.env` из `.env.example`. Checkout runner может меняться: Compose-файл берётся из checkout, но `.env`, backups и `.deploy` остаются в `DEPLOY_DIR`. `COMPOSE_PROJECT_NAME=practice-savva` обеспечивает одно имя containers/network/volume.

### Новая версия

`./scripts/deploy.sh IMAGE:SHA` проверяет reference и Docker, получает image реально работающего app container через `docker inspect`, при необходимости выполняет `docker pull`, пишет только `pending-image`, затем запускает `docker compose ... up -d --no-build app`. Успешное состояние меняется лишь после readiness. На успехе прежний image становится `previous-image`, новый — `current-image`.

Настройки ожидания: `HEALTH_ATTEMPTS`, `HEALTH_INTERVAL_SECONDS`, `HEALTH_REQUEST_TIMEOUT_SECONDS`, `HEALTH_MAX_WAIT_SECONDS`. Диагностика:

```bash
docker compose -p practice-savva ps
docker compose -p practice-savva logs --tail=300 app
cat "$DEPLOY_DIR/.deploy/current-image"
test ! -f "$DEPLOY_DIR/.deploy/pending-image" || cat "$DEPLOY_DIR/.deploy/pending-image"
curl -v http://localhost:8080/actuator/health/readiness
```

Если новая версия unhealthy, deploy восстанавливает `current-image`, повторно проверяет readiness и всё равно завершает исходное обновление с exit code 1. Если рабочей версии ещё не было, автоматический rollback невозможен и это явно сообщается.

### Ручной rollback

`./scripts/rollback.sh` требует `previous-image`, проверяет Docker/image/readiness и только после успеха обновляет `current-image`. После успешного rollback `previous-image` удаляется: повторный вызов не переключит систему обратно по циклу. При неуспехе rollback пытается восстановить image, работавший до операции, но возвращает exit code 1.

Deploy и rollback используют atomic `mkdir` lock. Живой параллельный процесс блокирует новую операцию; lock умершего PID считается stale. При прерывании `current-image` остаётся последней подтверждённой версией, а `pending-image` помогает диагностике; повторный deploy рабочей версии завершает восстановление.

PostgreSQL container и named volume не заменяются. Application rollback не равен database rollback: Flyway migrations автоматически не откатываются и должны быть backward-compatible (аддитивные изменения, defaults/nullable, двухфазное удаление).
