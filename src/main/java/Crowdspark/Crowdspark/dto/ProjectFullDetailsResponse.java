package Crowdspark.Crowdspark.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectFullDetailsResponse implements Serializable {

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
    private List<RewardTierResponse> rewards;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatorDto implements Serializable {
        private Long id;
        private String username;
        private String profileImage;
        private String about;
    }
}
