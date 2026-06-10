// src/main/java/Crowdspark/Crowdspark/service/ProjectReviewService.java

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.ProjectReviewRequest;
import Crowdspark.Crowdspark.dto.ProjectReviewResponse;
import Crowdspark.Crowdspark.dto.ReviewSummaryResponse;
import org.springframework.data.domain.Page;

public interface ProjectReviewService {

    /**
     * GET /api/projects/{id}/reviews/summary
     * Returns avg rating, distribution, and (if authenticated) the caller's own review.
     * currentUserId may be null for unauthenticated visitors.
     */
    ReviewSummaryResponse getSummary(Long projectId, Long currentUserId);

    /**
     * GET /api/projects/{id}/reviews
     * Paginated list of reviews, newest first.
     * currentUserId used only to set myReview flag on each item.
     */
    Page<ProjectReviewResponse> getReviews(Long projectId, int page, int size, Long currentUserId);

    /**
     * POST /api/projects/{id}/reviews
     * Only backers with at least one SUCCESSFUL donation can review.
     * One review per user per project — 409 if already reviewed.
     */
    ProjectReviewResponse submitReview(Long projectId,
                                       ProjectReviewRequest request,
                                       Long reviewerId);

    /**
     * PUT /api/projects/{id}/reviews/{reviewId}
     * Reviewer may update their own review.
     */
    ProjectReviewResponse updateReview(Long projectId,
                                       Long reviewId,
                                       ProjectReviewRequest request,
                                       Long reviewerId);

    /**
     * DELETE /api/projects/{id}/reviews/{reviewId}
     * Reviewer or admin can delete.
     */
    void deleteReview(Long projectId, Long reviewId, Long userId);
}
