package ru.practice.support.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.practice.support.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ErrorResponse> notFound(NotFoundException ex, HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
  }

  @ExceptionHandler({BusinessConflictException.class, OptimisticLockingFailureException.class})
  ResponseEntity<ErrorResponse> conflict(Exception ex, HttpServletRequest request) {
    String message =
        ex instanceof OptimisticLockingFailureException
            ? "Заявка была изменена другим запросом; повторите операцию"
            : ex.getMessage();
    return response(HttpStatus.CONFLICT, message, request, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> validation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
    return response(HttpStatus.BAD_REQUEST, "Ошибка валидации", request, fields);
  }

  @ExceptionHandler({
    InvalidRequestException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ErrorResponse> badRequest(Exception ex, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        ex instanceof InvalidRequestException ? ex.getMessage() : "Некорректный запрос",
        request,
        Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected request failure", ex);
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", request, Map.of());
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String message, HttpServletRequest request, Map<String, String> fields) {
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fields));
  }
}
