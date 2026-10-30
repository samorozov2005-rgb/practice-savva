package ru.practice.support.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practice.support.report.KotlinReportingService;
import ru.practice.support.report.SummaryReport;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
  private final KotlinReportingService reportingService;

  public ReportController(KotlinReportingService reportingService) {
    this.reportingService = reportingService;
  }

  @GetMapping("/summary")
  @Operation(
      summary = "Сводный отчёт",
      description = "ISO 8601, интервал [from, to); среднее время — секунды")
  public SummaryReport summary(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return reportingService.summary(from, to);
  }
}
