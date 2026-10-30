package ru.practice.support.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ticket_status_history")
public class TicketStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private Ticket ticket;

  @Enumerated(EnumType.STRING)
  @Column(name = "old_status", nullable = false, length = 20)
  private TicketStatus oldStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status", nullable = false, length = 20)
  private TicketStatus newStatus;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;

  protected TicketStatusHistory() {}

  public TicketStatusHistory(
      Ticket ticket, TicketStatus oldStatus, TicketStatus newStatus, Instant changedAt) {
    this.ticket = ticket;
    this.oldStatus = oldStatus;
    this.newStatus = newStatus;
    this.changedAt = changedAt;
  }

  public Long getId() {
    return id;
  }

  public Ticket getTicket() {
    return ticket;
  }

  public TicketStatus getOldStatus() {
    return oldStatus;
  }

  public TicketStatus getNewStatus() {
    return newStatus;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
