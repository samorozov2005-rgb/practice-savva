package ru.practice.support.mapper;

import java.time.Clock;
import org.springframework.stereotype.Component;
import ru.practice.support.domain.Ticket;
import ru.practice.support.domain.TicketStatus;
import ru.practice.support.domain.TicketStatusHistory;
import ru.practice.support.dto.CategoryResponse;
import ru.practice.support.dto.StatusHistoryResponse;
import ru.practice.support.dto.TicketResponse;

@Component
public class TicketMapper {
  private final Clock clock;

  public TicketMapper(Clock clock) {
    this.clock = clock;
  }

  public TicketResponse toResponse(Ticket ticket) {
    var category = ticket.getCategory();
    boolean open =
        ticket.getStatus() == TicketStatus.NEW || ticket.getStatus() == TicketStatus.IN_PROGRESS;
    boolean overdue = open && ticket.getDueAt().isBefore(clock.instant());
    return new TicketResponse(
        ticket.getId(),
        ticket.getTitle(),
        ticket.getDescription(),
        new CategoryResponse(category.getId(), category.getName(), category.getDescription()),
        ticket.getPriority(),
        ticket.getStatus(),
        ticket.getCreatedAt(),
        ticket.getDueAt(),
        ticket.getClosedAt(),
        ticket.getUpdatedAt(),
        ticket.getVersion(),
        overdue);
  }

  public StatusHistoryResponse toResponse(TicketStatusHistory history) {
    return new StatusHistoryResponse(
        history.getId(), history.getOldStatus(), history.getNewStatus(), history.getChangedAt());
  }
}
