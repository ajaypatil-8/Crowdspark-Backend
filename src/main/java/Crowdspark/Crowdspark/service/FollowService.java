// src/main/java/Crowdspark/Crowdspark/service/FollowService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.FollowResponse;
import Crowdspark.Crowdspark.dto.FollowStatusResponse;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface FollowService {

    /** Toggle follow — returns new state */
    FollowStatusResponse toggle(Long followerId, Long targetId);

    /** Check if follower is following target */
    FollowStatusResponse checkStatus(Long followerId, Long targetId);

    /** Get paginated list of people this user follows */
    Page<FollowResponse> getFollowing(Long userId, int page, int size);

    /** Get paginated list of this user's followers */
    Page<FollowResponse> getFollowers(Long userId, int page, int size);

    /** Feed of projects from creators this user follows */
    List<ProjectFeedResponse> getFollowedFeed(Long userId);
}
