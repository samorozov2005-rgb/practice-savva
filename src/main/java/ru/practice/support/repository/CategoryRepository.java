package ru.practice.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practice.support.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {}
