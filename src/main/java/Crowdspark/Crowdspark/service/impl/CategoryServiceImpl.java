package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.Category;
import Crowdspark.Crowdspark.repository.CategoryRepository;
import Crowdspark.Crowdspark.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public Category createCategory(String name) {
        categoryRepository.findByName(name).ifPresent(c -> {
            throw new RuntimeException("Category already exists");
        });
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    @Override
    @Cacheable(value = "categories", key = "'all'")
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
}
