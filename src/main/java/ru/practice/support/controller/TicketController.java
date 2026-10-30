package ru.practice.support.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practice.support.domain.TicketPriority;
import ru.practice.support.domain.TicketStatus;
import ru.practice.support.dto.*;
import ru.practice.support.service.TicketService;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
  private final TicketService service;

  public TicketController(TicketService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Создать заявку",
      responses = {
        @ApiResponse(responseCode = "201", description = "Заявка создана"),
        @ApiResponse(
            responseCode = "400",
            description = "Ошибка запроса",
            content =
                @Content(
                    examples =
                        @ExampleObject(
                            value =
                                "{\"status\":400,\"message\":\"Ошибка валидации\",\"fieldErrors\":{\"title\":\"must not be blank\"}}"))),
        @ApiResponse(responseCode = "404", description = "Категория не найдена")
      })
  public TicketResponse create(@Valid @RequestBody CreateTicketRequest request) {
    return service.create(request);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Получить заявку")
  public TicketResponse get(@PathVariable long id) {
    return service.get(id);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Изменить описание, приоритет и/или срок")
  public TicketResponse update(
      @PathVariable long id, @Valid @RequestBody UpdateTicketRequest request) {
    return service.update(id, request);
  }

  @PatchMapping("/{id}/status")
  @Operation(summary = "Изменить статус по разрешённому графу переходов")
  public TicketResponse changeStatus(
      @PathVariable long id, @Valid @RequestBody ChangeStatusRequest request) {
    return service.changeStatus(id, request.status());
  }

  @GetMapping("/{id}/history")
  @Operation(summary = "История переходов", description = "Порядок: changedAt ASC, затем id ASC")
  public List<StatusHistoryResponse> history(@PathVariable long id) {
    return service.history(id);
  }

  @GetMapping
  @Operation(summary = "Список заявок с фильтрацией")
  public Page<TicketResponse> list(
      @RequestParam(required = false) TicketStatus status,
      @RequestParam(required = false) TicketPriority priority,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) Boolean overdue,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return service.list(status, priority, categoryId, title, overdue, pageable);
  }
}
