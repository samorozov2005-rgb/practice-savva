# Отчёт по практике

## Цель и задача

Цель — устранить потерю обращений и ручной контроль, создав воспроизводимый backend заявок. Реализованы REST, бизнес-граф статусов, сроки, история, фильтры, статистика, PostgreSQL, тесты и эксплуатационный контур.

## Технологии и архитектура

Java 21 отвечает за web/domain/data слои; Spring Boot 3.5.16 связывает MVC, Validation, JPA, Actuator и конфигурацию. Flyway владеет схемой PostgreSQL, Hibernate только валидирует её. Kotlin 2.1.21 реализует работающий отчёт. Архитектура — понятный монолит controller/service/repository/entity/DTO/mapper без распределённых транзакций.

## Этапы и функциональность

Созданы Gradle Wrapper и миграции; затем REST и ограничения статусов; Kotlin-отчёт и тесты; multi-stage Docker/Compose, backup/restore, deploy/rollback и GitHub Actions; документация и материалы защиты. Optimistic `@Version` не позволяет конкурентным запросам тихо терять обновления. Статус и история находятся в одной `@Transactional` операции.

## Kotlin-решения

1. `SummaryReport` — `data class`, поэтому DTO краток и имеет корректные value semantics. Поле `Double?` честно представляет отсутствие завершённых заявок, без magic value `0`.
2. Коллекционные операции `groupingBy/eachCount`, sequence, `mapNotNull` и safe-call `closedAt?.let` делают расчёт единой выборки компактным и null-safe. Java-интерфейс `ReportingDataProvider` изолирует Kotlin от деталей JPA; Java controller напрямую вызывает Kotlin service.

## Тестирование

После Mac-финализации выполняется 31 тест: Java/Kotlin unit, 12 PostgreSQL integration и два E2E-сценария. Добавлены проверки актуальной optimistic version в REST response, реального transaction rollback при ошибке DB trigger на history и HTTP 409 для optimistic conflict. Использованы фиксированный Clock, Mockito/MockK и настоящий `postgres:17-alpine` Testcontainers. Итог: 31/31, JaCoCo 93.0% instructions, 76.7% branches, 95.9% lines; Spotless и coverage verification прошли.

## Контейнеризация и CI/CD

Runtime работает непривилегированным пользователем на Temurin JRE 21; Compose обеспечивает health dependency и persistent volume. Локально собраны и проверены ARM64 images, а отдельная Buildx-сборка `linux/amd64` завершилась успешно (запуск на Windows x86-64 ещё не выполнялся). Deploy/rollback проверены в изолированном Compose-проекте на двух локальных tags, включая failed readiness, автоматическое восстановление, lock и interruption.

Workflow разделяет GitHub-hosted verification, multi-arch GHCR publication с полным SHA и deploy на защищённый self-hosted Linux runner. Manual dispatch принимает 40-character SHA, безопасно формирует GHCR reference; `actionlint` прошёл с конфигурацией custom label. PR никогда не запускается на студенческом runner. Реальные GitHub publication/deploy ожидают внешней инфраструктуры и не заявлены выполненными.

## Дефекты и ограничения

Mac-аудит выявил старую `version` в response до flush и ненадёжное состояние deploy; оба дефекта исправлены и закреплены тестами/локальными сценариями. Реальный отказ history insert через PostgreSQL trigger подтвердил атомарный rollback. Пограничный риск `dueAt == now` закреплён отдельным тестом. В проект не входят UI, безопасность пользователей, уведомления и внешние каналы. Фактическое время не учитывалось; 72 часа в requirements — плановая оценка.

## Заключение

Получен переносимый учебный сервис, в котором требования связаны с кодом, тестами и эксплуатационными инструкциями. Локальный Mac-этап проверен и описан в `mac-finalization-report.md`. Оставшаяся внешняя работа — создать репозиторий студента, включить branch/environment protection, GHCR и self-hosted runner, запустить pipeline на Windows/WSL2 и подтвердить целевой deployment.
