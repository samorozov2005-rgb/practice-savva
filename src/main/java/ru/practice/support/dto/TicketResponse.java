package ru.practice.support.dto;

import java.time.Instant;
import ru.practice.support.domain.TicketPriority;
import ru.practice.support.domain.TicketStatus;

public record TicketResponse(
    Long id,
    String title,
    String description,
    CategoryResponse category,
    TicketPriority priority,
    TicketStatus status,
    Instant createdAt,
    Instant dueAt,
    Instant closedAt,
    Instant updatedAt,
    long version,
    boolean overdue) {}
