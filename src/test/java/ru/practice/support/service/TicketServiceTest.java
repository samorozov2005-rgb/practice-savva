package ru.practice.support.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.practice.support.domain.*;
import ru.practice.support.dto.*;
import ru.practice.support.exception.*;
import ru.practice.support.mapper.TicketMapper;
import ru.practice.support.repository.*;

class TicketServiceTest {
  private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");
  private TicketRepository tickets;
  private CategoryRepository categories;
  private TicketStatusHistoryRepository history;
  private TicketMapper mapper;
  private TicketService service;
  private Category category;

  @BeforeEach
  void setUp() {
    tickets = mock(TicketRepository.class);
    categories = mock(CategoryRepository.class);
    history = mock(TicketStatusHistoryRepository.class);
    mapper = mock(TicketMapper.class);
    service =
        new TicketService(tickets, categories, history, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    category = new Category("Сеть", "Сетевые проблемы");
  }

  @Test
  void createsTicketWithNewStatusAndServerTime() {
    when(categories.findById(1L)).thenReturn(Optional.of(category));
    when(tickets.save(any())).thenAnswer(i -> i.getArgument(0));
    when(mapper.toResponse(any(Ticket.class))).thenReturn(mock(TicketResponse.class));

    service.create(
        new CreateTicketRequest(
            " VPN ", " Не работает ", 1L, TicketPriority.HIGH, NOW.plusSeconds(3600)));

    ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
    verify(tickets).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(TicketStatus.NEW);
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW);
    assertThat(captor.getValue().getTitle()).isEqualTo("VPN");
  }

  @Test
  void rejectsDeadlineAtCurrentMoment() {
    var request = new CreateTicketRequest("x", "y", 1L, TicketPriority.LOW, NOW);
    assertThatThrownBy(() -> service.create(request)).isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(categories);
  }

  @Test
  void rejectsUnknownCategory() {
    when(categories.findById(99L)).thenReturn(Optional.empty());
    var request = new CreateTicketRequest("x", "y", 99L, TicketPriority.LOW, NOW.plusSeconds(1));
    assertThatThrownBy(() -> service.create(request)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void returnsNotFoundForUnknownTicket() {
    when(tickets.findById(42L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(42L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void updatesOpenTicket() {
    Ticket ticket = ticket();
    when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
    when(mapper.toResponse(ticket)).thenReturn(mock(TicketResponse.class));
    service.update(1L, new UpdateTicketRequest("new", TicketPriority.HIGH, NOW.plusSeconds(7200)));
    assertThat(ticket.getDescription()).isEqualTo("new");
    assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);
  }

  @Test
  void rejectsEmptyPatch() {
    assertThatThrownBy(() -> service.update(1L, new UpdateTicketRequest(null, null, null)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void rejectsEditingResolvedTicket() {
    Ticket ticket = ticket();
    ticket.changeStatus(TicketStatus.RESOLVED, NOW);
    when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
    assertThatThrownBy(() -> service.update(1L, new UpdateTicketRequest("new", null, null)))
        .isInstanceOf(BusinessConflictException.class);
  }

  @Test
  void allowsNewToInProgressAndWritesHistory() {
    Ticket ticket = ticket();
    when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
    when(mapper.toResponse(ticket)).thenReturn(mock(TicketResponse.class));
    service.changeStatus(1L, TicketStatus.IN_PROGRESS);
    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    verify(history).save(any(TicketStatusHistory.class));
  }

  @Test
  void resolvesInProgressAndSetsClosedAt() {
    Ticket ticket = ticket();
    ticket.changeStatus(TicketStatus.IN_PROGRESS, NOW.minusSeconds(60));
    when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
    when(mapper.toResponse(ticket)).thenReturn(mock(TicketResponse.class));
    service.changeStatus(1L, TicketStatus.RESOLVED);
    assertThat(ticket.getClosedAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsNewToResolved() {
    when(tickets.findById(1L)).thenReturn(Optional.of(ticket()));
    assertThatThrownBy(() -> service.changeStatus(1L, TicketStatus.RESOLVED))
        .isInstanceOf(BusinessConflictException.class);
    verifyNoInteractions(history);
  }

  @Test
  void rejectsHistoryForUnknownTicket() {
    when(tickets.existsById(7L)).thenReturn(false);
    assertThatThrownBy(() -> service.history(7L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void rejectsUnsafeSortProperty() {
    var page = PageRequest.of(0, 20, Sort.by("unknown"));
    assertThatThrownBy(() -> service.list(null, null, null, null, null, page))
        .isInstanceOf(InvalidRequestException.class);
  }

  private Ticket ticket() {
    return new Ticket(
        "VPN",
        "broken",
        category,
        TicketPriority.MEDIUM,
        NOW.minusSeconds(600),
        NOW.plusSeconds(3600));
  }
}
