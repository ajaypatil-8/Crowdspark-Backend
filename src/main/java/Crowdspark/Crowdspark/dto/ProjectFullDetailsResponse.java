package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectFullDetailsResponse {

    private Long id;
    private String title;
    private String shortDescription;
    private String fullDescription;
    private String category;

    private Double goalAmount;
    private Double currentAmount;
    private Integer fundedPercentage;
    private Long daysLeft;
    private LocalDateTime deadline;

    private CreatorDto creator;

    private String thumbnailUrl;
    private List<String> previewVideos;
    private List<String> galleryImages;
    private List<String> storyImages;

    @Data
    @Builder
    public static class CreatorDto {
        private Long id;
        private String username;
        private String profileImage;
        private String about;
    }
}
