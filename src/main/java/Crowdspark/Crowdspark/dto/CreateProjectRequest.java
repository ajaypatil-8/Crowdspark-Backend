package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.MediaType;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateProjectRequest {

    @NotBlank
    private String title;

    @NotBlank
    @Size(max = 300)
    private String shortDescription;

    @NotBlank
    private String fullDescription;

    @NotBlank
    private String location;

    @NotNull
    @Positive
    private Double goalAmount;

    @NotNull
    private LocalDateTime deadline;

    @NotEmpty
    private List<Long> categoryIds;

    @NotEmpty
    private List<ProjectMediaRequest> media;

    // ---- INNER DTO ----
    @Data
    public static class ProjectMediaRequest {

        @NotBlank
        private String mediaUrl;     // Cloudinary URL

        @NotNull
        private MediaType mediaType; // IMAGE / VIDEO

        @NotNull
        private MediaUsage usage;    // THUMBNAIL, CARD_VIDEO, STORY_IMAGE...

        private Integer displayOrder;
    }
}
