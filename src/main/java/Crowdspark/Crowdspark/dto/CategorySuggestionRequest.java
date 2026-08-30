// src/main/java/Crowdspark/Crowdspark/dto/CategorySuggestionRequest.java
// Feature #47 — AI Auto-Tagging & Category Detection

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategorySuggestionRequest {

    // Deliberately just title + shortDescription -- both fields exist on
    // the wizard's own Step 1, alongside the category picker this feeds
    // into, so no other step's data needs to be ready yet.
    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
    private String title;

    @NotBlank(message = "Short description is required")
    @Size(max = 300, message = "Short description must be under 300 characters")
    private String shortDescription;
}
