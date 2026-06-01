package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.CreateCategoryRequest;
import Crowdspark.Crowdspark.entity.Category;
import Crowdspark.Crowdspark.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Campaign categories — public list")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Create a category",
            description = "Admin only. Creates a new campaign category.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<Category>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        Category cat = categoryService.createCategory(request.getName());
        return ResponseEntity.ok(ApiResponse.ok("Category created", cat));
    }

    @Operation(summary = "Get all categories",
            description = "Public endpoint. Returns all available campaign categories.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Category>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getAllCategories()));
    }
}