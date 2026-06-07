// src/main/java/Crowdspark/Crowdspark/repository/ProjectRepository.java
// CHANGES: Added searchWithFts() native query using PostgreSQL tsvector.
// The existing findForExplore() is kept for no-keyword browsing (faster).

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

    // ── Feed / Dashboard ───────────────────────────────────────────────────────

    List<Project> findByStatusOrderByCreatedAtDesc(ProjectStatus status);

    List<Project> findByCreatorOrderByCreatedAtDesc(User creator);

    List<Project> findByStatus(ProjectStatus status);

    long countByCreator(User creator);

    long countByCreatorAndStatus(User creator, ProjectStatus status);

    // ── Deadline scheduler (Feature #2) ───────────────────────────────────────

    @Query("SELECT p FROM Project p WHERE p.status = 'APPROVED' AND p.deadline < :now")
    List<Project> findExpiredApprovedProjects(@Param("now") LocalDateTime now);

    // ── Explore: no keyword — fast JPQL path ─────────────────────────────────
    // Used when user is just browsing without a search term.

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

    // ── Explore: with keyword — FTS path ─────────────────────────────────────
    // Used when a search term is present. Combines:
    //   1. tsvector @@ plainto_tsquery  — full-text relevance match
    //   2. ILIKE fallback               — catches partial words / short queries
    //      e.g. "art" matches "artist" even if not in tsvector
    // Results ordered by: sort param → ts_rank → newest.

    @Query(value = """
        SELECT DISTINCT p.*
        FROM   projects p
        LEFT   JOIN project_categories pc ON pc.project_id = p.id
        WHERE  p.status = 'APPROVED'
        AND   (:categoryId IS NULL OR pc.category_id = :categoryId)
        AND   (
                  p.search_vector @@ plainto_tsquery('english', :keyword)
               OR LOWER(p.title)             LIKE LOWER('%' || :keyword || '%')
               OR LOWER(p.short_description) LIKE LOWER('%' || :keyword || '%')
              )
        ORDER BY
            CASE WHEN :sort IN ('MOST_FUNDED','TRENDING')
                 THEN p.current_amount END DESC NULLS LAST,
            ts_rank(p.search_vector, plainto_tsquery('english', :keyword)) DESC,
            p.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(DISTINCT p.id)
        FROM   projects p
        LEFT   JOIN project_categories pc ON pc.project_id = p.id
        WHERE  p.status = 'APPROVED'
        AND   (:categoryId IS NULL OR pc.category_id = :categoryId)
        AND   (
                  p.search_vector @@ plainto_tsquery('english', :keyword)
               OR LOWER(p.title)             LIKE LOWER('%' || :keyword || '%')
               OR LOWER(p.short_description) LIKE LOWER('%' || :keyword || '%')
              )
        """,
        nativeQuery = true)
    Page<Project> searchWithFts(
            @Param("categoryId") Long   categoryId,
            @Param("keyword")    String keyword,
            @Param("sort")       String sort,
            Pageable             pageable
    );
}
