# Архитектура и диаграммы

## Компоненты

```mermaid
flowchart LR
  Client[Swagger / HTTP client] --> C[Java REST controllers]
  C --> S[Java TicketService]
  C --> K[KotlinReportingService]
  S --> R[Spring Data repositories]
  K --> P[Java ReportingDataProvider]
  P --> R
  R --> H[Hibernate/JPA]
  H --> DB[(PostgreSQL)]
  F[Flyway] --> DB
```

## ER-модель

```mermaid
erDiagram
  CATEGORY ||--o{ TICKET : classifies
  TICKET ||--o{ TICKET_STATUS_HISTORY : records
  CATEGORY { bigint id PK; varchar name UK; varchar description }
  TICKET { bigint id PK; varchar title; varchar description; bigint category_id FK; varchar priority; varchar status; timestamptz created_at; timestamptz due_at; timestamptz closed_at; timestamptz updated_at; bigint version }
  TICKET_STATUS_HISTORY { bigint id PK; bigint ticket_id FK; varchar old_status; varchar new_status; timestamptz changed_at }
```

## Жизненный цикл

```mermaid
stateDiagram-v2
  [*] --> NEW
  NEW --> IN_PROGRESS
  NEW --> CANCELLED
  IN_PROGRESS --> RESOLVED
  IN_PROGRESS --> CANCELLED
  RESOLVED --> [*]
  CANCELLED --> [*]
```

`RESOLVED` и `CANCELLED` терминальны. Статус и история меняются в одной транзакции; optimistic version превращает конкурентный конфликт в HTTP 409.

## CI/CD

```mermaid
flowchart LR
  PR[Pull request] --> V[format + tests + coverage]
  Main[push main] --> V
  V -->|success on main| B[build multi-arch image]
  B --> GHCR[GHCR : commit SHA]
  GHCR --> D[self-hosted runner / protected environment]
  D --> Health{readiness OK?}
  Health -->|yes| Done[record current image]
  Health -->|no| Rollback[previous image; same DB volume]
```
