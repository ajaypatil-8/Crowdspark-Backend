package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data

public class AssignCategoriesRequest {
    @NotEmpty(message = "At least one category id is required")
    private List<Long> categoryIds;
}
