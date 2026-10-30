package ru.practice.support.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practice.support.domain.*;
import ru.practice.support.dto.*;
import ru.practice.support.exception.BusinessConflictException;
import ru.practice.support.exception.InvalidRequestException;
import ru.practice.support.exception.NotFoundException;
import ru.practice.support.mapper.TicketMapper;
import ru.practice.support.repository.CategoryRepository;
import ru.practice.support.repository.TicketRepository;
import ru.practice.support.repository.TicketStatusHistoryRepository;

@Service
public class TicketService {
  private static final Set<String> SORT_FIELDS =
      Set.of("id", "title", "priority", "status", "createdAt", "dueAt", "updatedAt");
  private final TicketRepository tickets;
  private final CategoryRepository categories;
  private final TicketStatusHistoryRepository history;
  private final TicketMapper mapper;
  private final Clock clock;

  public TicketService(
      TicketRepository tickets,
      CategoryRepository categories,
      TicketStatusHistoryRepository history,
      TicketMapper mapper,
      Clock clock) {
    this.tickets = tickets;
    this.categories = categories;
    this.history = history;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public TicketResponse create(CreateTicketRequest request) {
    Instant now = clock.instant();
    requireFuture(request.dueAt(), now);
    Category category =
        categories
            .findById(request.categoryId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Категория %d не найдена".formatted(request.categoryId())));
    Ticket ticket =
        new Ticket(
            request.title().trim(),
            request.description().trim(),
            category,
            request.priority(),
            now,
            request.dueAt());
    return mapper.toResponse(tickets.save(ticket));
  }

  @Transactional(readOnly = true)
  public TicketResponse get(long id) {
    return mapper.toResponse(find(id));
  }

  @Transactional
  public TicketResponse update(long id, UpdateTicketRequest request) {
    if (request.description() == null && request.priority() == null && request.dueAt() == null) {
      throw new InvalidRequestException("Нужно передать хотя бы одно изменяемое поле");
    }
    Ticket ticket = find(id);
    requireEditable(ticket);
    Instant now = clock.instant();
    if (request.dueAt() != null) requireFuture(request.dueAt(), now);
    ticket.update(
        request.description() == null ? null : request.description().trim(),
        request.priority(),
        request.dueAt(),
        now);
    tickets.flush();
    return mapper.toResponse(ticket);
  }

  @Transactional
  public TicketResponse changeStatus(long id, TicketStatus newStatus) {
    Ticket ticket = find(id);
    TicketStatus oldStatus = ticket.getStatus();
    if (!allowed(oldStatus, newStatus)) {
      throw new BusinessConflictException(
          "Переход %s → %s запрещён".formatted(oldStatus, newStatus));
    }
    Instant now = clock.instant();
    ticket.changeStatus(newStatus, now);
    history.save(new TicketStatusHistory(ticket, oldStatus, newStatus, now));
    tickets.flush();
    return mapper.toResponse(ticket);
  }

  @Transactional(readOnly = true)
  public List<StatusHistoryResponse> history(long id) {
    if (!tickets.existsById(id)) throw new NotFoundException("Заявка %d не найдена".formatted(id));
    return history.findByTicketIdOrderByChangedAtAscIdAsc(id).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<TicketResponse> list(
      TicketStatus status,
      TicketPriority priority,
      Long categoryId,
      String title,
      Boolean overdue,
      Pageable pageable) {
    pageable
        .getSort()
        .forEach(
            order -> {
              if (!SORT_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException(
                    "Недопустимое поле сортировки: " + order.getProperty());
              }
            });
    Specification<Ticket> spec =
        Specification.allOf(
            TicketSpecifications.status(status),
            TicketSpecifications.priority(priority),
            TicketSpecifications.category(categoryId),
            TicketSpecifications.title(title),
            TicketSpecifications.overdue(overdue, clock.instant()));
    return tickets.findAll(spec, pageable).map(mapper::toResponse);
  }

  private Ticket find(long id) {
    return tickets
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Заявка %d не найдена".formatted(id)));
  }

  private static void requireEditable(Ticket ticket) {
    if (ticket.getStatus() == TicketStatus.RESOLVED
        || ticket.getStatus() == TicketStatus.CANCELLED) {
      throw new BusinessConflictException("Завершённую или отменённую заявку нельзя редактировать");
    }
  }

  private static void requireFuture(Instant dueAt, Instant now) {
    if (!dueAt.isAfter(now))
      throw new InvalidRequestException("Срок выполнения должен быть позже текущего момента");
  }

  private static boolean allowed(TicketStatus oldStatus, TicketStatus newStatus) {
    return (oldStatus == TicketStatus.NEW
            && (newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.CANCELLED))
        || (oldStatus == TicketStatus.IN_PROGRESS
            && (newStatus == TicketStatus.RESOLVED || newStatus == TicketStatus.CANCELLED));
  }
}
