# Передача проекта студенту

Этот документ применяется на следующем этапе. На Mac не создавался Git-репозиторий, не подключались GitHub/GHCR/runner и не выполнялся удалённый deploy.

## 1. Подготовка GitHub

1. На аккаунте студента создать пустой repository без автоматически созданных файлов.
2. Добавить разработчика через Settings → Collaborators с минимально достаточным доступом.
3. Уже на компьютере студента и только после проверки содержимого выполнить первый `git init`, commit и push. Точную ссылку repository взять из GitHub, не придумывать.
4. Сделать `main` основной веткой. В Branch protection потребовать pull request и успешный job `verify`.
5. В Actions разрешить workflows и packages. Первый PR должен выполнить только GitHub-hosted проверки.
6. После первого merge проверить package `support-ticket-service` в GHCR и доступ repository к нему. Workflow использует встроенный `GITHUB_TOKEN`; персональный token в исходники не добавлять.
7. Создать Environment `student-stand` и, если тариф позволяет, включить required reviewer/deployment branch `main`.

## 2. Подготовка Windows 11

На Windows устанавливаются: WSL2, Docker Desktop и при желании Windows Terminal. Команда PowerShell администратора: `wsl --install -d Ubuntu`, затем перезагрузка. В Docker Desktop включаются WSL2 engine и интеграция с выбранной Ubuntu.

Внутри Ubuntu устанавливаются Git, `curl` и GitHub Actions self-hosted runner для Linux x64. Проект, постоянный deploy-каталог и runner лучше хранить в Linux filesystem WSL, а не под `/mnt/c`, чтобы избежать проблем с правами и производительностью. JDK, Gradle, Kotlin и PostgreSQL на Windows для Compose-запуска не нужны.

Проверки в Ubuntu:

```bash
docker version
docker compose version
git --version
uname -m                    # ожидается x86_64
```

## 3. Локальный запуск

```bash
git clone <URL_РЕПОЗИТОРИЯ_СТУДЕНТА>
cd practice-savva
cp .env.example .env
# открыть .env и заменить POSTGRES_PASSWORD уникальным значением
docker compose config
docker compose up --build -d
docker compose ps
docker compose exec db sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
curl -fsS http://localhost:8080/actuator/health/readiness
```

Swagger: <http://localhost:8080/swagger-ui.html>. Затем выполнить запросы из `requests.http`: категории, создание заявки, смена статуса, history и Kotlin summary. Остановка с сохранением данных: `docker compose stop`; не применять `down -v`.

## 4. Self-hosted runner

1. GitHub repository → Settings → Actions → Runners → New self-hosted runner.
2. Выбрать Linux / x64. Выполнить в Ubuntu только команды, которые покажет GitHub. Registration token не сохранять в проекте и документах.
3. При конфигурации добавить custom label `student-stand`; стандартные labels должны включать `self-hosted`, `linux`, `x64`.
4. Создать постоянный каталог, например выбранный студентом каталог внутри Ubuntu. Скопировать туда `.env.example` как `.env`, задать уникальный пароль. Путь не должен быть каталогом временного `_work/...` checkout.
5. Repository Settings → Actions → Variables создать `DEPLOY_DIR` со значением этого абсолютного каталога. Workflow передаёт его deploy-скриптам; там сохраняются `.env`, `.deploy` и backups.
6. Дать пользователю runner доступ к Docker по стандартной документации Docker и проверить `docker info` из той же учётной записи.
7. Установить runner как службу по официальной команде, выданной GitHub. Не запускать произвольные PR jobs на этом runner: workflow использует его только для deploy из `main`/manual dispatch `main`.

Секрет БД остаётся только в `$DEPLOY_DIR/.env`. GitHub token выдаётся job автоматически. Не печатать `.env` и не прикладывать его к artifacts.

## 5. Проверка CI/CD

1. Создать тестовую ветку и небольшое безопасное изменение документации.
2. Открыть pull request. Дождаться `verify`: Java 21, Spotless, все тесты Testcontainers/E2E, JaCoCo и artifacts.
3. Убедиться, что PR не создал publish/deploy jobs на self-hosted runner.
4. После review выполнить merge в `main`.
5. Проверить публикацию multi-arch image `ghcr.io/<owner>/support-ticket-service:<полный-SHA>`.
6. Разрешить protected Environment deploy и проверить online runner, readiness и `current-image` в `$DEPLOY_DIR/.deploy`.
7. Создать заявку и запомнить ID.
8. Выполнить второе изменение через PR/merge; проверить, что `previous-image` содержит первый SHA, а данные сохранились.
9. Выполнить `DEPLOY_DIR=<каталог> ./scripts/rollback.sh`; проверить возврат первого image и readiness.
10. Повторно получить заявку/историю. PostgreSQL volume должен сохраниться; Flyway автоматически назад не откатывается.

Ручной dispatch принимает полный 40-символьный commit SHA уже опубликованного image, а не полное имя image.

## 6. Чек-лист перед защитой

- [ ] Docker Desktop запущен, WSL2/Ubuntu доступны.
- [ ] Runner online и имеет labels `self-hosted`, `linux`, `x64`, `student-stand`.
- [ ] `$DEPLOY_DIR/.env` существует, секрет не находится в repository.
- [ ] `docker compose -p practice-savva ps` показывает healthy PostgreSQL и Spring Boot.
- [ ] Readiness возвращает `UP`, Swagger открывается.
- [ ] Локальные тесты или последний GitHub `verify` успешны.
- [ ] Pipeline показывает verify → publish → protected deploy.
- [ ] Есть два известных SHA image для демонстрации обновления и rollback.
- [ ] Тестовая заявка переживает update/rollback.
- [ ] Студент умеет выполнить `docker compose logs`, проверить `.deploy/current-image`, backup/restore и объяснить, почему DB migrations не откатываются вместе с image.
