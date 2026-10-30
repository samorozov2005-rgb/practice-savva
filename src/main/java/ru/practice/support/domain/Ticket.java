package ru.practice.support.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tickets")
public class Ticket {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 4000)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TicketPriority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TicketStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "due_at", nullable = false)
  private Instant dueAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected Ticket() {}

  public Ticket(
      String title,
      String description,
      Category category,
      TicketPriority priority,
      Instant createdAt,
      Instant dueAt) {
    this.title = title;
    this.description = description;
    this.category = category;
    this.priority = priority;
    this.status = TicketStatus.NEW;
    this.createdAt = createdAt;
    this.dueAt = dueAt;
    this.updatedAt = createdAt;
  }

  public void update(String description, TicketPriority priority, Instant dueAt, Instant now) {
    if (description != null) this.description = description;
    if (priority != null) this.priority = priority;
    if (dueAt != null) this.dueAt = dueAt;
    this.updatedAt = now;
  }

  public void changeStatus(TicketStatus newStatus, Instant now) {
    this.status = newStatus;
    this.closedAt = newStatus == TicketStatus.RESOLVED ? now : null;
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Category getCategory() {
    return category;
  }

  public TicketPriority getPriority() {
    return priority;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }
}
