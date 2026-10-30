package ru.practice.support.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practice.support.domain.Ticket;

public interface TicketRepository
    extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
  @EntityGraph(attributePaths = "category")
  @Query(
      """
      select t from Ticket t
      where t.createdAt >= :from and t.createdAt < :to
      order by t.createdAt, t.id
      """)
  List<Ticket> findForReport(@Param("from") Instant from, @Param("to") Instant to);
}
