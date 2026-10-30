package ru.practice.support.report;

import java.time.Instant;
import java.util.List;
import ru.practice.support.domain.Ticket;

public interface ReportingDataProvider {
  List<Ticket> findCreatedBetween(Instant from, Instant to);
}
