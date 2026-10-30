package ru.practice.support.dto;

import jakarta.validation.constraints.NotNull;
import ru.practice.support.domain.TicketStatus;

public record ChangeStatusRequest(@NotNull TicketStatus status) {}
