package ru.practice.support.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.practice.support.repository.TicketRepository;
import ru.practice.support.repository.TicketStatusHistoryRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketApiIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("support_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TicketStatusHistoryRepository history;
  @Autowired TicketRepository tickets;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    history.deleteAll();
    tickets.deleteAll();
  }

  @Test
  void flywaySeedsCategories() throws Exception {
    mvc.perform(get("/api/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].name").value("Оборудование"));
  }

  @Test
  void createsPersistsAndGetsTicket() throws Exception {
    long id = create("Printer", "Paper jam", "HIGH");
    mvc.perform(get("/api/tickets/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Printer"))
        .andExpect(jsonPath("$.status").value("NEW"));
    assertThat(tickets.count()).isEqualTo(1);
  }

  @Test
  void returnsStructuredValidationError() throws Exception {
    mvc.perform(
            post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "title",
                            "",
                            "description",
                            "x",
                            "categoryId",
                            1,
                            "priority",
                            "LOW",
                            "dueAt",
                            NOW.plusSeconds(100)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Ошибка валидации"))
        .andExpect(jsonPath("$.fieldErrors.title").exists());
  }

  @Test
  void returnsNotFoundWithoutLeakingInternals() throws Exception {
    mvc.perform(get("/api/tickets/99999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.path").value("/api/tickets/99999"));
  }

  @Test
  void combinesFiltersPaginationAndSorting() throws Exception {
    create("VPN alpha", "x", "HIGH");
    create("VPN beta", "x", "LOW");
    create("Printer", "x", "HIGH");
    mvc.perform(
            get("/api/tickets")
                .param("title", "vpn")
                .param("priority", "HIGH")
                .param("size", "1")
                .param("sort", "title,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].title").value("VPN alpha"));
  }

  @Test
  void statusHistoryIsOrderedAndPersisted() throws Exception {
    long id = create("VPN", "x", "MEDIUM");
    changeStatus(id, "IN_PROGRESS", 200);
    changeStatus(id, "RESOLVED", 200);
    mvc.perform(get("/api/tickets/{id}/history", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].oldStatus").value("NEW"))
        .andExpect(jsonPath("$[1].newStatus").value("RESOLVED"));
  }

  @Test
  void invalidTransitionIsAtomic() throws Exception {
    long id = create("VPN", "x", "MEDIUM");
    changeStatus(id, "RESOLVED", 409);
    mvc.perform(get("/api/tickets/{id}", id)).andExpect(jsonPath("$.status").value("NEW"));
    mvc.perform(get("/api/tickets/{id}/history", id)).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void staleConcurrentUpdateIsRejected() throws Exception {
    long id = create("Concurrent", "original", "MEDIUM");
    var stale = tickets.findById(id).orElseThrow();
    jdbc.update(
        "update tickets set description = ?, version = version + 1 where id = ?", "winner", id);

    stale.update("loser", null, null, NOW.plusSeconds(1));
    assertThatThrownBy(() -> tickets.saveAndFlush(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
    assertThat(
            jdbc.queryForObject("select description from tickets where id = ?", String.class, id))
        .isEqualTo("winner");
  }

  @Test
  void updateAndStatusResponsesContainFlushedVersions() throws Exception {
    long id = create("Versioned", "v0", "MEDIUM");
    mvc.perform(get("/api/tickets/{id}", id)).andExpect(jsonPath("$.version").value(0));

    mvc.perform(
            patch("/api/tickets/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("description", "v1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));

    mvc.perform(
            patch("/api/tickets/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("status", "IN_PROGRESS"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(get("/api/tickets/{id}", id)).andExpect(jsonPath("$.version").value(2));
  }

  @Test
  void historyInsertFailureRollsBackTicketStatusInPostgres() throws Exception {
    long id = create("Rollback", "history must be atomic", "HIGH");
    jdbc.execute(
        """
        CREATE OR REPLACE FUNCTION fail_ticket_history_insert() RETURNS trigger
        LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced history failure'; END $$
        """);
    jdbc.execute(
        """
        CREATE TRIGGER trg_fail_ticket_history_insert
        BEFORE INSERT ON ticket_status_history
        FOR EACH ROW EXECUTE FUNCTION fail_ticket_history_insert()
        """);
    try {
      changeStatus(id, "IN_PROGRESS", 500);
    } finally {
      jdbc.execute(
          "DROP TRIGGER IF EXISTS trg_fail_ticket_history_insert ON ticket_status_history");
      jdbc.execute("DROP FUNCTION IF EXISTS fail_ticket_history_insert()");
    }

    assertThat(jdbc.queryForObject("select status from tickets where id = ?", String.class, id))
        .isEqualTo("NEW");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ticket_status_history where ticket_id = ?", Long.class, id))
        .isZero();
  }

  @Test
  @Tag("e2e")
  void scenarioAFullTicketLifecycleAndReport() throws Exception {
    long id = create("Router", "Offline", "HIGH");
    mvc.perform(get("/api/tickets/{id}", id)).andExpect(status().isOk());
    mvc.perform(
            patch("/api/tickets/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsBytes(
                        Map.of("description", "Rebooted", "priority", "MEDIUM"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Rebooted"));
    changeStatus(id, "IN_PROGRESS", 200);
    changeStatus(id, "RESOLVED", 200);
    mvc.perform(get("/api/tickets/{id}/history", id)).andExpect(jsonPath("$.length()").value(2));
    mvc.perform(
            get("/api/reports/summary")
                .param("from", NOW.minusSeconds(1).toString())
                .param("to", NOW.plusSeconds(1).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdCount").value(1))
        .andExpect(jsonPath("$.byStatus.RESOLVED").value(1))
        .andExpect(jsonPath("$.averageResolutionTimeSeconds").value(0.0));
  }

  @Test
  @Tag("e2e")
  void scenarioBTerminalTicketRejectsEditWithoutMutation() throws Exception {
    long id = create("Account", "Lock", "LOW");
    changeStatus(id, "CANCELLED", 200);
    mvc.perform(
            patch("/api/tickets/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("description", "must not change"))))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/tickets/{id}", id))
        .andExpect(jsonPath("$.description").value("Lock"))
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(get("/api/tickets/{id}/history", id)).andExpect(jsonPath("$.length()").value(1));
  }

  private long create(String title, String description, String priority) throws Exception {
    String body =
        mvc.perform(
                post("/api/tickets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsBytes(
                            Map.of(
                                "title",
                                title,
                                "description",
                                description,
                                "categoryId",
                                1,
                                "priority",
                                priority,
                                "dueAt",
                                NOW.plusSeconds(86400)))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode node = json.readTree(body);
    return node.get("id").asLong();
  }

  private void changeStatus(long id, String status, int expectedStatus) throws Exception {
    mvc.perform(
            patch("/api/tickets/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("status", status))))
        .andExpect(status().is(expectedStatus));
  }
}
