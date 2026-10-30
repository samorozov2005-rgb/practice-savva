# Технологии на примерах проекта

- **Java 21** — типизированные domain/service/controller и records DTO.
- **Kotlin/JVM** — совместим с Java bytecode; отчёт использует data class, nullable `Double?` и коллекции.
- **Spring Boot** — запускает Tomcat и автоконфигурирует MVC, JPA, Flyway, Actuator.
- **REST/HTTP** — ресурсы доступны по URL; POST создаёт (201), GET читает (200/404), PATCH частично меняет (200/400/409).
- **DTO** — безопасный контракт, независимый от lazy relations и структуры таблиц.
- **JPA/Hibernate** — отображают entity на SQL; `@Version` реализует optimistic locking. Hibernate не создаёт схему.
- **PostgreSQL** — транзакционная БД, FK/check/index и `timestamptz`.
- **Flyway** — последовательно применяет V1/V2 и хранит checksums/history.
- **Gradle Wrapper** — одинаковая версия сборщика без системного Gradle.
- **JUnit 5** — тестовый framework; **Mockito/MockK** изолируют Java/Kotlin units.
- **Testcontainers** — временный PostgreSQL в Docker вместо несовместимой H2.
- **JaCoCo** — измеряет исполненный JVM bytecode Java и Kotlin.
- **Docker** — одинаковый runtime; multi-stage не переносит JDK/Gradle в финальный слой.
- **Compose** — связывает app/db, health, variables и persistent volume.
- **GitHub Actions/CI/CD** — автоматическая проверка; затем immutable SHA image и контролируемый deploy/rollback.

OpenAPI — машинное описание контракта, Swagger UI — интерактивный клиент. Actuator readiness отвечает, готово ли приложение принимать трафик.
