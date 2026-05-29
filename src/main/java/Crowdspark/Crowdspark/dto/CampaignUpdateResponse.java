// src/main/java/Crowdspark/Crowdspark/dto/CampaignUpdateResponse.java
package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CampaignUpdateResponse {
    private Long   id;
    private Long   projectId;
    private String projectTitle;
    private Long   authorId;
    private String authorUsername;
    private String authorProfileImage;
    private String title;
    private String content;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
