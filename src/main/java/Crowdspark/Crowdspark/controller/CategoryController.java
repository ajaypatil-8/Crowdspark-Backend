package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.CreateCategoryRequest;
import Crowdspark.Crowdspark.entity.Category;
import Crowdspark.Crowdspark.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // create category (admin later)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Category createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.createCategory(request.getName());
    }

    // get all categories (frontend dropdown)
    @GetMapping
    public List<Category> getAll() {
        return categoryService.getAllCategories();
    }
}
