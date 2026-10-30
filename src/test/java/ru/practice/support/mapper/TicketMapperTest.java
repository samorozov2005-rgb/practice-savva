package ru.practice.support.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import ru.practice.support.domain.Category;
import ru.practice.support.domain.Ticket;
import ru.practice.support.domain.TicketPriority;

class TicketMapperTest {
  private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");

  @Test
  void deadlineBeforeNowIsOverdue() {
    var ticket =
        new Ticket(
            "x",
            "y",
            new Category("c", null),
            TicketPriority.LOW,
            NOW.minusSeconds(10),
            NOW.minusNanos(1));
    assertThat(new TicketMapper(Clock.fixed(NOW, ZoneOffset.UTC)).toResponse(ticket).overdue())
        .isTrue();
  }

  @Test
  void deadlineEqualNowIsNotOverdue() {
    var ticket =
        new Ticket(
            "x", "y", new Category("c", null), TicketPriority.LOW, NOW.minusSeconds(10), NOW);
    assertThat(new TicketMapper(Clock.fixed(NOW, ZoneOffset.UTC)).toResponse(ticket).overdue())
        .isFalse();
  }
}
