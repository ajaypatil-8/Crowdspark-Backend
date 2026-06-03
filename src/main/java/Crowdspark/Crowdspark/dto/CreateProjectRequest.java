// src/main/java/Crowdspark/Crowdspark/dto/CreateProjectRequest.java
// CHANGES: Added @Size constraints on title, fullDescription, location
// to prevent oversized payloads and potential injection via large inputs.

package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.MediaType;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CreateProjectRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 255, message = "Title must be between 5 and 255 characters")
    private String title;

    @NotBlank(message = "Short description is required")
    @Size(max = 300, message = "Short description must be under 300 characters")
    private String shortDescription;

    @NotBlank(message = "Full description is required")
    @Size(min = 50, max = 15000, message = "Full description must be between 50 and 15,000 characters")
    private String fullDescription;

    @NotBlank(message = "Location is required")
    @Size(max = 200, message = "Location must be under 200 characters")
    private String location;

    @NotNull(message = "Goal amount is required")
    @Positive(message = "Goal amount must be greater than zero")
    @Max(value = 100_000_000, message = "Goal amount cannot exceed ₹10 crore")
    private Double goalAmount;

    @NotNull(message = "Deadline is required")
    @Future(message = "Deadline must be a future date")
    private LocalDateTime deadline;

    @NotEmpty(message = "At least one category is required")
    @Size(max = 5, message = "Maximum 5 categories allowed")
    private List<Long> categoryIds;

    @NotEmpty(message = "At least one media item is required")
    @Size(max = 20, message = "Maximum 20 media items allowed")
    private List<ProjectMediaRequest> media;

    @Size(max = 10, message = "Maximum 10 reward tiers allowed")
    private List<RewardTierRequest> rewardTiers = new ArrayList<>();

    @Data
    public static class ProjectMediaRequest {

        @NotBlank(message = "Media URL is required")
        @Size(max = 500, message = "Media URL must be under 500 characters")
        private String mediaUrl;

        @NotNull(message = "Media type is required")
        private MediaType mediaType;

        @NotNull(message = "Media usage is required")
        private MediaUsage usage;

        @Min(value = 0)
        @Max(value = 100)
        private Integer displayOrder;
    }
}
