package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.Category;

import java.util.List;

public interface CategoryService {

    Category createCategory(String name);

    List<Category> getAllCategories();
}
