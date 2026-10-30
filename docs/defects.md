# Журнал дефектов

## DEF-001 — новый concurrency-тест не компилировался

- **Симптом:** чистый `./gradlew spotlessApply clean check` завершился на `compileTestJava`: метод `assertThatThrownBy` не найден.
- **Воспроизведение:** добавить проверку stale update и запустить чистую компиляцию тестов.
- **Причина:** при добавлении AssertJ assertion отсутствовал соответствующий static import.
- **Исправление:** добавлен `import static org.assertj.core.api.Assertions.assertThatThrownBy;`.
- **Регрессия:** тест `staleConcurrentUpdateIsRejected` теперь компилируется и проверяет, что `@Version` отклоняет проигравшее конкурентное изменение; весь clean check повторён.

Во время проектирования также была отдельно выделена регрессионно опасная граница просрочки: использование `!dueAt.isAfter(now)` ошибочно считало бы равный дедлайн просроченным. Реализация сразу использует строгий `isBefore`, а поведение закреплено тестом `TicketMapperTest.deadlineEqualNowIsNotOverdue`. Это предотвращённый риск, а не обнаруженный после запуска дефект.

## DEF-002 — REST response мог содержать старую optimistic version

- **Симптом:** `update()` и `changeStatus()` создавали `TicketResponse` до обязательного Hibernate flush; поле `version` могло оставаться предыдущим, хотя после commit в БД уже было увеличено.
- **Воспроизведение:** создать заявку, выполнить PATCH и сравнить `version` PATCH response с последующим GET.
- **Причина:** increment `@Version` выполняется при SQL update/flush, а mapper вызывался раньше.
- **Исправление:** перед mapping добавлен `tickets.flush()`; при status change тот же flush одновременно заставляет вставить history.
- **Регрессия:** `updateAndStatusResponsesContainFlushedVersions` проверяет версии 0 → 1 → 2 в PostgreSQL REST integration test.

## DEF-003 — deploy/rollback преждевременно и ненадёжно управляли состоянием

- **Симптом:** previous image извлекался regex из Compose JSON, состояние previous записывалось до успешного readiness, registry pull в rollback подавлялся через `|| true`, Compose-проект зависел от каталога checkout.
- **Воспроизведение:** первый deploy, failed readiness image и запуск из другого deployment directory давали неоднозначное состояние/невозможность надёжного восстановления.
- **Причина:** отсутствовали структурированный `docker inspect`, persistent `DEPLOY_DIR`, atomic state и operation lock.
- **Исправление:** общий `compose-common.sh`, стабильное project name, atomic current/previous/pending, readiness limits, lock, `--no-build` и проверяемое автоматическое восстановление.
- **Регрессия:** 28.10.2026 локально выполнены first/same/v2 deploy, manual rollback, failed deploy auto-rollback, failed rollback recovery, missing image/Docker/previous, invalid reference, lock и interruption; подробности в `mac-finalization-report.md`.

Реальные дефекты, найденные при последующих изменениях, следует добавлять с симптомом, воспроизведением, причиной, исправлением и именем регрессионного теста.
