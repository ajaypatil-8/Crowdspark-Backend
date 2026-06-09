// src/main/java/Crowdspark/Crowdspark/dto/FollowResponse.java
package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/** A single follower or following entry */
@Data
@Builder
public class FollowResponse {
    private Long   userId;
    private String username;
    private String name;
    private String profileImageUrl;
    private String bio;
    private boolean isCreator;
    private long    followerCount;
    private long    totalProjects;
    private LocalDateTime followedAt;
}
