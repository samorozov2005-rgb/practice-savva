# Сценарий онлайн-защиты (10–15 минут)

1. **0:00–1:00.** Сформулировать проблему и открыть `requirements.md`: показать FR-03 и acceptance criteria.
2. **1:00–2:30.** Открыть `TicketService.changeStatus`, миграцию и тест `invalidTransitionIsAtomic`: требование → код → тест.
3. **2:30–3:30.** `docker compose ps`, readiness и Swagger. Объяснить PostgreSQL volume/Flyway.
4. **3:30–5:00.** В Swagger получить категории, создать заявку и выполнить GET.
5. **5:00–6:30.** PATCH description/priority, затем NEW→IN_PROGRESS→RESOLVED; показать `closedAt`.
6. **6:30–7:15.** Получить history и объяснить транзакционность/порядок.
7. **7:15–8:15.** Вызвать summary; открыть Kotlin service и Java interface, назвать nullable average.
8. **8:15–9:00.** Попытаться изменить RESOLVED: показать 409 и неизменность GET/history.
9. **9:00–10:00.** Показать отчёты тестов/coverage и Testcontainers PostgreSQL.
10. **10:00–11:30.** Открыть workflow: PR verify, main publish SHA, protected self-hosted deploy. Не говорить, что GitHub прошёл, пока он не подключён.
11. **11:30–12:30.** Показать `deploy.sh`, readiness и `rollback.sh`; подчеркнуть сохранение volume и совместимые миграции.
12. **12:30–14:00.** Быстрая диагностика: logs, actuator, flyway history; backup/restore в отдельную БД.

До защиты после передачи: создать GitHub repository, включить branch protection и environment approval, зарегистрировать WSL2 runner с labels, настроить GHCR permissions, выполнить merge в main и зафиксировать успешные jobs/deploy. Это будущая проверка, а не результат локальной разработки.
