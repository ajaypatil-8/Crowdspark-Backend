// src/main/java/Crowdspark/Crowdspark/dto/CampaignUpdateRequest.java
package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CampaignUpdateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be under 255 characters")
    private String title;

    @NotBlank(message = "Content is required")
    @Size(max = 5000, message = "Content must be under 5000 characters")
    private String content;

    /** Optional — Cloudinary URL uploaded separately */
    private String imageUrl;
}
