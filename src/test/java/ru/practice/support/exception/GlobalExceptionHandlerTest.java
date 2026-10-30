package ru.practice.support.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import ru.practice.support.domain.Ticket;

class GlobalExceptionHandlerTest {
  @Test
  void optimisticLockingFailureIsReturnedAsConflict() {
    var request = new MockHttpServletRequest("PATCH", "/api/tickets/1");
    var handler =
        new GlobalExceptionHandler(
            Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC));

    var response =
        handler.conflict(new ObjectOptimisticLockingFailureException(Ticket.class, 1L), request);

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).contains("изменена другим запросом");
  }
}
