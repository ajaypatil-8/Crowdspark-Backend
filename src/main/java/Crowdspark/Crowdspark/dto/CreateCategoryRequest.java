package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data

public class CreateCategoryRequest {
    @NotBlank(message = "Category name must not be blank")
    private String name;
}
