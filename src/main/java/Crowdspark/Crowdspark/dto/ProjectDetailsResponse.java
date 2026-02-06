package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectDetailsResponse {

    private Long id;
    private String title;
    private String shortDescription;
    private String fullDescription;
    private String location;

    private Double goalAmount;
    private Double currentAmount;
    private Integer fundedPercentage;
    private Integer daysLeft;
    private Long backersCount;

    private LocalDateTime deadline;
    private String status;

    // media
    private String thumbnailUrl;
    private List<String> cardVideos;
    private List<String> galleryImages;
    private List<String> storyImages;
    private List<String> mainVideos;

    // creator
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
