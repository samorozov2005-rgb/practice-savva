package ru.practice.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import ru.practice.support.domain.TicketPriority;

public record CreateTicketRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 4000) String description,
    @NotNull Long categoryId,
    @NotNull TicketPriority priority,
    @NotNull Instant dueAt) {}
