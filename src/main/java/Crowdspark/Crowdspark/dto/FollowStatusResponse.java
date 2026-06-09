// src/main/java/Crowdspark/Crowdspark/dto/FollowStatusResponse.java
package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

/** Returned by toggle and check endpoints */
@Data
@Builder
public class FollowStatusResponse {
    private Long    targetUserId;
    private boolean following;         // true = you now follow them
    private long    followerCount;     // updated count for the target user
}
