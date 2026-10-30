package ru.practice.support.report;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.practice.support.domain.Ticket;
import ru.practice.support.repository.TicketRepository;

@Component
public class JpaReportingDataProvider implements ReportingDataProvider {
  private final TicketRepository repository;

  public JpaReportingDataProvider(TicketRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Ticket> findCreatedBetween(Instant from, Instant to) {
    return repository.findForReport(from, to);
  }
}
