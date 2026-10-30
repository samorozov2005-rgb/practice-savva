package ru.practice.support.dto;

import java.time.Instant;
import ru.practice.support.domain.TicketStatus;

public record StatusHistoryResponse(
    Long id, TicketStatus oldStatus, TicketStatus newStatus, Instant changedAt) {}
