// src/main/java/Crowdspark/Crowdspark/dto/CreateCategoryRequest.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • name: added @Size(min=2, max=100) and @Pattern to only allow
//            readable category names (letters, digits, spaces, hyphens)
//            Was only @NotBlank — a category named "<script>" would be
//            persisted and potentially rendered on public pages.

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    @Pattern(
        regexp  = "^[\\p{L}\\p{N}\\s&'.,()\\-]+$",
        message = "Category name may only contain letters, digits, spaces and common punctuation"
    )
    private String name;

    /**
     * Optional slug — auto-generated from name if omitted.
     * Must be URL-safe: lowercase letters, digits and hyphens only.
     */
    @Size(max = 120, message = "Slug must be 120 characters or less")
    @Pattern(
        regexp  = "^$|^[a-z0-9]+(-[a-z0-9]+)*$",
        message = "Slug must be lowercase letters, digits and hyphens (e.g. 'clean-energy')"
    )
    private String slug;
}
