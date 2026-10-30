# Установка на Windows и подключение GitHub

## 1. Запуск приложения через Docker

1. Включить виртуализацию в BIOS/UEFI. В PowerShell администратора выполнить `wsl --install -d Ubuntu`, перезагрузиться и создать Linux-пользователя.
2. Установить Docker Desktop. В Settings включить WSL 2 engine и интеграцию с Ubuntu. Проверить в Ubuntu: `docker version` и `docker compose version`.
3. Скопировать проект в каталог WSL, например `~/support-ticket-service` (не в системные каталоги Windows).
4. В Ubuntu: `cd ~/support-ticket-service`, `cp .env.example .env`, открыть `.env` и заменить `POSTGRES_PASSWORD`.
5. Выполнить `docker compose up --build -d`; затем `docker compose ps` и `curl -fsS localhost:8080/actuator/health/readiness`.
6. Открыть в Windows <http://localhost:8080/swagger-ui.html>. JDK/Gradle/Kotlin устанавливать не нужно.
7. Остановка с данными: `docker compose stop`; продолжение: `docker compose start`. Не использовать `down -v`, если данные нужны.

## 2. GitHub и self-hosted runner в WSL2

1. Установить Git в Ubuntu и создать пустой GitHub repository. Только студент выполняет инициализацию/публикацию проекта.
2. В GitHub Settings → Actions разрешить packages; создать Environment `student-stand` с required reviewer. Защитить `main`: PR и обязательный job `verify`.
3. Settings → Actions → Runners → New self-hosted runner → Linux x64; выполнить показанные GitHub команды внутри WSL2. Не копировать registration token в файлы проекта.
4. Добавить runner labels `linux` и `student-stand`, настроить его как службу по официальной инструкции GitHub. Убедиться, что пользователь runner входит в группу `docker` и `docker info` работает без sudo.
5. Для private GHCR проверить package access репозитория. Workflow использует временный `GITHUB_TOKEN`; постоянные токены в `.env` не нужны.
6. Сделать PR: должен работать только `verify`. После review/merge в `main` должны последовательно пройти `verify`, `publish`, approval environment и `deploy`.
7. Проверить на ПК `docker compose ps`, Swagger и созданную ранее заявку. Для отката выполнить `./scripts/rollback.sh` или redeploy нужного SHA image.

Runner выполняет доверенный код только из защищённой `main`; PR job остаётся на GitHub-hosted машине. Перед переустановкой/экспериментами делайте `./scripts/backup.sh`.
