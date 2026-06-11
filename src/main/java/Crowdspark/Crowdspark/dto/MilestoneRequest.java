// src/main/java/Crowdspark/Crowdspark/dto/MilestoneRequest.java

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class MilestoneRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be 255 characters or fewer")
    private String title;

    @Size(max = 2000, message = "Description must be 2000 characters or fewer")
    private String description;

    /**
     * Optional funding target this milestone represents.
     * Must be positive if provided.
     */
    @Positive(message = "Target amount must be positive")
    private Double targetAmount;

    /**
     * Optional explicit sort order.
     * If omitted the service appends the milestone at the end.
     */
    @Min(value = 0, message = "Sort order must be >= 0")
    private Integer sortOrder;
}
