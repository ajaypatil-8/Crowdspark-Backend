// src/main/java/Crowdspark/Crowdspark/dto/GenerateDescriptionRequest.java
// Feature #39 — AI Campaign Description Generator (request)

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GenerateDescriptionRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
    private String title;

    @NotEmpty(message = "At least one bullet point is required")
    @Size(max = 15, message = "Maximum 15 bullet points allowed")
    private List<@Size(max = 280, message = "Each bullet point must be under 280 characters") String> bulletPoints;

    // Optional context — not required, but helps the model tailor tone/scope
    @Size(max = 100, message = "Category must be under 100 characters")
    private String category;

    @Size(max = 200, message = "Location must be under 200 characters")
    private String location;
}
