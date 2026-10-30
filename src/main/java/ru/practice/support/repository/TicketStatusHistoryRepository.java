package ru.practice.support.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.practice.support.domain.TicketStatusHistory;

public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, Long> {
  List<TicketStatusHistory> findByTicketIdOrderByChangedAtAscIdAsc(Long ticketId);
}
