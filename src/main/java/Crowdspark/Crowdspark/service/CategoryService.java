package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CategoryResponse;
import Crowdspark.Crowdspark.dto.CreateCategoryRequest;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CreateCategoryRequest request);

    List<CategoryResponse> getAll();
}
