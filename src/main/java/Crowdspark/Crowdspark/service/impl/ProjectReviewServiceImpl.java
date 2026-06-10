// src/main/java/Crowdspark/Crowdspark/service/impl/ProjectReviewServiceImpl.java

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.ProjectReviewRequest;
import Crowdspark.Crowdspark.dto.ProjectReviewResponse;
import Crowdspark.Crowdspark.dto.ReviewSummaryResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectReview;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.ProjectReviewRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.ProjectReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectReviewServiceImpl implements ProjectReviewService {

    private final ProjectReviewRepository reviewRepository;
    private final ProjectRepository       projectRepository;
    private final DonationRepository      donationRepository;
    private final UserRepository          userRepository;

    // ── Summary ──────────────────────────────────────────────────────────────

    @Override
    public ReviewSummaryResponse getSummary(Long projectId, Long currentUserId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }

        long total      = reviewRepository.countByProject_Id(projectId);
        Double avgRaw   = reviewRepository.getAverageRating(projectId);
        Double avg      = avgRaw == null ? null
                : BigDecimal.valueOf(avgRaw).setScale(1, RoundingMode.HALF_UP).doubleValue();

        Map<Integer, Long> dist = buildDistribution(projectId);

        ProjectReviewResponse myReview = null;
        boolean canReview = false;

        if (currentUserId != null) {
            myReview = reviewRepository
                .findByProject_IdAndReviewer_Id(projectId, currentUserId)
                .map(r -> toResponse(r, currentUserId))
                .orElse(null);

            boolean alreadyReviewed = myReview != null;
            boolean hasBacked = donationRepository.existsByBacker_IdAndProject_Id(
                    currentUserId, projectId);
            // must be a backer and not yet reviewed
            canReview = hasBacked && !alreadyReviewed;
        }

        return ReviewSummaryResponse.builder()
                .projectId(projectId)
                .totalReviews(total)
                .averageRating(avg)
                .ratingDistribution(dist)
                .myReview(myReview)
                .canReview(canReview)
                .build();
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Override
    public Page<ProjectReviewResponse> getReviews(Long projectId, int page, int size,
                                                   Long currentUserId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return reviewRepository
                .findByProject_IdOrderByCreatedAtDesc(projectId, PageRequest.of(page, size))
                .map(r -> toResponse(r, currentUserId));
    }

    // ── Submit ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectReviewResponse submitReview(Long projectId,
                                               ProjectReviewRequest request,
                                               Long reviewerId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found"));

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        // Gate: must have at least one successful donation to this project
        boolean hasBacked = donationRepository.existsByBacker_IdAndProject_Id(
                reviewerId, projectId);
        if (!hasBacked) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only backers who supported this project can leave a review");
        }

        // Gate: one review per user per project
        if (reviewRepository.existsByProject_IdAndReviewer_Id(projectId, reviewerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have already reviewed this project");
        }

        ProjectReview review = new ProjectReview();
        review.setProject(project);
        review.setReviewer(reviewer);
        review.setRating(request.getRating());
        review.setTitle(request.getTitle() != null ? request.getTitle().trim() : null);
        review.setContent(request.getContent() != null ? request.getContent().trim() : null);

        ProjectReview saved = reviewRepository.save(review);
        log.info("Review submitted: projectId={}, reviewerId={}, rating={}",
                projectId, reviewerId, request.getRating());

        return toResponse(saved, reviewerId);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectReviewResponse updateReview(Long projectId, Long reviewId,
                                               ProjectReviewRequest request,
                                               Long reviewerId) {
        ProjectReview review = findAndAuthorize(projectId, reviewId, reviewerId);

        review.setRating(request.getRating());
        review.setTitle(request.getTitle() != null ? request.getTitle().trim() : null);
        review.setContent(request.getContent() != null ? request.getContent().trim() : null);

        ProjectReview saved = reviewRepository.save(review);
        log.info("Review updated: reviewId={}, reviewerId={}", reviewId, reviewerId);
        return toResponse(saved, reviewerId);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteReview(Long projectId, Long reviewId, Long userId) {
        // Admins bypass ownership check; reviewers must own it
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        boolean isAdmin = user.getRoles().contains(Role.ADMIN);
        if (isAdmin) {
            ProjectReview review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Review not found"));
            reviewRepository.delete(review);
        } else {
            ProjectReview review = findAndAuthorize(projectId, reviewId, userId);
            reviewRepository.delete(review);
        }
        log.info("Review deleted: reviewId={}, by userId={}", reviewId, userId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProjectReview findAndAuthorize(Long projectId, Long reviewId, Long userId) {
        ProjectReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Review not found"));

        if (!review.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Review does not belong to this project");
        }
        if (!review.getReviewer().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only modify your own review");
        }
        return review;
    }

    private Map<Integer, Long> buildDistribution(Long projectId) {
        // Pre-fill all stars with 0
        Map<Integer, Long> dist = new HashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);

        List<Object[]> rows = reviewRepository.getRatingDistribution(projectId);
        for (Object[] row : rows) {
            Integer star  = ((Number) row[0]).intValue();
            Long    count = ((Number) row[1]).longValue();
            dist.put(star, count);
        }
        return dist;
    }

    private ProjectReviewResponse toResponse(ProjectReview r, Long currentUserId) {
        User reviewer = r.getReviewer();
        return ProjectReviewResponse.builder()
                .id(r.getId())
                .projectId(r.getProject().getId())
                .reviewerId(reviewer.getId())
                .reviewerName(reviewer.getName())
                .reviewerUsername(reviewer.getUsername())
                .reviewerProfileImageUrl(reviewer.getProfileImageUrl())
                .rating(r.getRating())
                .title(r.getTitle())
                .content(r.getContent())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .myReview(currentUserId != null && currentUserId.equals(reviewer.getId()))
                .build();
    }
}
