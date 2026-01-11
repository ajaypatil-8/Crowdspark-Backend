package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CategoryResponse;
import Crowdspark.Crowdspark.dto.CreateCategoryRequest;
import Crowdspark.Crowdspark.entity.Category;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.CategoryRepository;
import Crowdspark.Crowdspark.service.CategoryService;
import Crowdspark.Crowdspark.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;
    private final ModelMapper modelMapper;

    @Override
    public CategoryResponse create(CreateCategoryRequest request) {

        categoryRepository.findByName(request.getName())
                .ifPresent(c -> {
                    throw new AuthException("Category already exists");
                });

        Category category = new Category();
        category.setName(request.getName());

        Category saved = categoryRepository.save(category);

        auditLogService.log(
                null,
                "CATEGORY_CREATED",
                "CATEGORY",
                saved.getId()
        );


        return CategoryResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll()
                .stream()
                .map(c -> CategoryResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .createdAt(c.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
