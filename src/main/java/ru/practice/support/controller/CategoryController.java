package ru.practice.support.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practice.support.dto.CategoryResponse;
import ru.practice.support.service.CategoryService;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
  private final CategoryService service;

  public CategoryController(CategoryService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Получить справочник категорий")
  public List<CategoryResponse> findAll() {
    return service.findAll();
  }
}
