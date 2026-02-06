package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectFeedResponse {

    private Long id;
    private String title;
    private String shortDescription;

    // thumbnail
    private String thumbnailUrl;

    // first preview video (optional)
    private String previewVideoUrl;

    private String category;

    private Double goalAmount;
    private Double currentAmount;

    private Integer fundedPercentage;
    private Integer daysLeft;
    private Long backersCount;

    // creator info (hover card)
    private CreatorDto creator;

    @Data
    @Builder
    public static class CreatorDto {
        private Long id;
        private String username;
        private String profileImage;

        private String about;
        private String joinedAt;

        private Long totalProjects;
        private Long totalBackers;
    }
}
