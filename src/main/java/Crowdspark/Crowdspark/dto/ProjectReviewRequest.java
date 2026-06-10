// src/main/java/Crowdspark/Crowdspark/dto/ProjectReviewRequest.java

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProjectReviewRequest {

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @Size(max = 255, message = "Title must be 255 characters or fewer")
    private String title;

    @Size(min = 10, max = 3000, message = "Review content must be between 10 and 3000 characters")
    private String content;
}
