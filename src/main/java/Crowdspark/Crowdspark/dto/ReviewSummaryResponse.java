// src/main/java/Crowdspark/Crowdspark/dto/ReviewSummaryResponse.java

package Crowdspark.Crowdspark.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewSummaryResponse {

    private Long   projectId;
    private long   totalReviews;

    /**
     * Average rounded to 1 decimal place.
     * Null when there are no reviews yet.
     */
    private Double averageRating;

    /**
     * Histogram: key = star count (1–5), value = number of reviews with that star.
     * Missing stars have value 0.
     * Example: { 1:2, 2:0, 3:5, 4:12, 5:20 }
     */
    private Map<Integer, Long> ratingDistribution;

    /**
     * The current user's own review for this project.
     * Null when the user has not reviewed yet or is not authenticated.
     */
    private ProjectReviewResponse myReview;

    /** True when the authenticated user is eligible to submit a review (backed + not yet reviewed) */
    private boolean canReview;
}
