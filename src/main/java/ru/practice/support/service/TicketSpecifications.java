package ru.practice.support.service;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;
import ru.practice.support.domain.Ticket;
import ru.practice.support.domain.TicketPriority;
import ru.practice.support.domain.TicketStatus;

public final class TicketSpecifications {
  private TicketSpecifications() {}

  public static Specification<Ticket> status(TicketStatus value) {
    return value == null ? null : (root, query, cb) -> cb.equal(root.get("status"), value);
  }

  public static Specification<Ticket> priority(TicketPriority value) {
    return value == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), value);
  }

  public static Specification<Ticket> category(Long id) {
    return id == null ? null : (root, query, cb) -> cb.equal(root.get("category").get("id"), id);
  }

  public static Specification<Ticket> title(String value) {
    return value == null || value.isBlank()
        ? null
        : (root, query, cb) ->
            cb.like(cb.lower(root.get("title")), "%" + value.trim().toLowerCase() + "%");
  }

  public static Specification<Ticket> overdue(Boolean value, Instant now) {
    if (value == null) return null;
    return (root, query, cb) -> {
      var isOpen = root.get("status").in(TicketStatus.NEW, TicketStatus.IN_PROGRESS);
      var isOverdue = cb.and(isOpen, cb.lessThan(root.get("dueAt"), now));
      return value ? isOverdue : cb.not(isOverdue);
    };
  }
}
