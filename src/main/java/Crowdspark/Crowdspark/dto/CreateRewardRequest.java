package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data

public class CreateRewardRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    @NotNull(message = "Minimum amount is required")
    @PositiveOrZero(message = "Minimum amount must be zero or positive")
    private Double minAmount;

    @Size(max = 2000)
    private String description;
}

