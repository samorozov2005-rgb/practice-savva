package ru.practice.support.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import ru.practice.support.domain.TicketPriority;

public record UpdateTicketRequest(
    @Size(min = 1, max = 4000) String description, TicketPriority priority, Instant dueAt) {}
