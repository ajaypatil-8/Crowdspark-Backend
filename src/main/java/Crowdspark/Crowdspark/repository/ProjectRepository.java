// src/main/java/Crowdspark/Crowdspark/repository/ProjectRepository.java
// CHANGE: Added findExpiredApprovedProjects query for the scheduler

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByStatusOrderByCreatedAtDesc(ProjectStatus status);

    List<Project> findByCreatorOrderByCreatedAtDesc(User creator);

    List<Project> findByStatus(ProjectStatus status);

    long countByCreator(User creator);

    long countByCreatorAndStatus(User creator, ProjectStatus status);

    // Explore feed
    @Query("""
        SELECT DISTINCT p FROM Project p
        LEFT JOIN p.categories c
        WHERE p.status = 'APPROVED'
        AND (:categoryId IS NULL OR c.id = :categoryId)
        AND (:keyword IS NULL
             OR LOWER(p.title) LIKE :keyword
             OR LOWER(p.shortDescription) LIKE :keyword)
    """)
    Page<Project> findForExplore(
        @Param("categoryId") Long categoryId,
        @Param("keyword")    String keyword,
        Pageable pageable
    );

    // ── NEW: used by DeadlineSchedulerService every hour ─────────────────────
    // Finds all APPROVED projects whose deadline has passed.
    // These need to be transitioned to FUNDED or FAILED.
    @Query("SELECT p FROM Project p WHERE p.status = 'APPROVED' AND p.deadline < :now")
    List<Project> findExpiredApprovedProjects(@Param("now") LocalDateTime now);
}
