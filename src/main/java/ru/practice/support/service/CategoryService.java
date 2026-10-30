package ru.practice.support.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practice.support.dto.CategoryResponse;
import ru.practice.support.repository.CategoryRepository;

@Service
public class CategoryService {
  private final CategoryRepository repository;

  public CategoryService(CategoryRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> findAll() {
    return repository.findAll().stream()
        .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getDescription()))
        .toList();
  }
}
