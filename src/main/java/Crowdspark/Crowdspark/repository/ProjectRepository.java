package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Feed (only approved/active)
    List<Project> findByStatusOrderByCreatedAtDesc(ProjectStatus status);

    // Creator dashboard
    List<Project> findByCreatorOrderByCreatedAtDesc(User creator);

    // Admin pending review
    List<Project> findByStatus(ProjectStatus status);

    long countByCreator(User creator);

    long countByCreatorAndStatus(User creator, ProjectStatus status);

    // Section 3 — Explore with optional category + keyword filter
    // FIX: keyword passed pre-lowercased with % wildcards from service layer.
    // Avoids CONCAT('%',:param,'%') which causes PostgreSQL to infer param
    // as bytea → "function lower(bytea) does not exist" error.
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
        @Param("keyword") String keyword,
        Pageable pageable
    );
}
