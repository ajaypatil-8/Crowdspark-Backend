// src/main/java/Crowdspark/Crowdspark/repository/ProjectReviewRepository.java

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.ProjectReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectReviewRepository extends JpaRepository<ProjectReview, Long> {

    Page<ProjectReview> findByProject_IdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    Optional<ProjectReview> findByProject_IdAndReviewer_Id(Long projectId, Long reviewerId);

    // AUDIT FIX (Feature #11/#19): needed by GDPR account deletion, which
    // previously never touched this table — a "deleted" user's review text
    // stayed fully attached to their (now anonymised) account forever.
    List<ProjectReview> findByReviewer_Id(Long reviewerId);

    boolean existsByProject_IdAndReviewer_Id(Long projectId, Long reviewerId);

    long countByProject_Id(Long projectId);

    /** Average star rating for a project. Returns null if no reviews yet. */
    @Query("SELECT AVG(r.rating) FROM ProjectReview r WHERE r.project.id = :projectId")
    Double getAverageRating(@Param("projectId") Long projectId);

    /**
     * Returns rows of (rating, count) for a project.
     * Used to build the star-distribution histogram.
     */
    @Query("SELECT r.rating, COUNT(r) FROM ProjectReview r " +
           "WHERE r.project.id = :projectId " +
           "GROUP BY r.rating ORDER BY r.rating")
    List<Object[]> getRatingDistribution(@Param("projectId") Long projectId);
}
