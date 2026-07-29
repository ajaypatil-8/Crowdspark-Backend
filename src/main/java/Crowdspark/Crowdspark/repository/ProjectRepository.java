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

    // AUDIT FIX (Feature #18): batched version of countByCreator, used to avoid
    // firing one COUNT query per row when rendering a page of followers/
    // following (see FollowServiceImpl.toFollowResponse).
    @Query("SELECT p.creator.id, COUNT(p) FROM Project p WHERE p.creator.id IN :creatorIds GROUP BY p.creator.id")
    List<Object[]> countByCreatorIds(@Param("creatorIds") List<Long> creatorIds);

    // AUDIT FIX (Feature #18): getFollowedFeed() used to fetch EVERY APPROVED
    // project platform-wide (findByStatusOrderByCreatedAtDesc with no bound)
    // and filter down to followed creators in application code — meaning it
    // re-scans and re-loads the entire APPROVED-projects table into memory on
    // every single call, and only gets slower as that table grows. This pushes
    // both the creator-ID filter AND the top-20 limit into the query itself,
    // so the DB only ever returns rows that were actually going to be used.
    // "Top20" is a real Spring Data JPA keyword — it adds a LIMIT clause.
    List<Project> findTop20ByCreator_IdInAndStatusOrderByCreatedAtDesc(
            List<Long> creatorIds, ProjectStatus status);

    // ── Deadline scheduler (Feature #2) ───────────────────────────────────────

    @Query("SELECT p FROM Project p WHERE p.status = 'APPROVED' AND p.deadline < :now")
    List<Project> findExpiredApprovedProjects(@Param("now") LocalDateTime now);

    // ── Explore: no keyword — fast JPQL path ─────────────────────────────────
    // Used when user is just browsing without a search term.
    //
    // AUDIT FIX (Feature #16): minGoal/maxGoal used to be filtered in Java
    // AFTER this query already paginated — see ProjectServiceImpl history.
    // That meant totalElements/totalPages reflected only what survived
    // filtering on the one page already fetched, so applying a goal-range
    // filter silently broke pagination (under-reporting how many matching
    // projects actually exist, and never even considering matches sitting on
    // pages 2+). Filtering here instead means the DB — not Java after the
    // fact — decides what "matches", so Page's own counts stay correct.

    @Query("""
        SELECT DISTINCT p FROM Project p
        LEFT JOIN p.categories c
        WHERE p.status = 'APPROVED'
        AND (:categoryId IS NULL OR c.id = :categoryId)
        AND (:keyword IS NULL
             OR LOWER(p.title) LIKE :keyword
             OR LOWER(p.shortDescription) LIKE :keyword)
        AND (:minGoal IS NULL OR p.goalAmount >= :minGoal)
        AND (:maxGoal IS NULL OR p.goalAmount <= :maxGoal)
    """)
    Page<Project> findForExplore(
            @Param("categoryId") Long categoryId,
            @Param("keyword")    String keyword,
            @Param("minGoal")    Double minGoal,
            @Param("maxGoal")    Double maxGoal,
            Pageable pageable
    );

    // ── Explore: with keyword — FTS path ─────────────────────────────────────
    // Used when a search term is present. Combines:
    //   1. tsvector @@ plainto_tsquery  — full-text relevance match
    //   2. ILIKE fallback               — catches partial words / short queries
    //      e.g. "art" matches "artist" even if not in tsvector
    // Results ordered by: sort param → ts_rank → newest.
    //
    // BUG FIX (Feature #16): the previous version used `SELECT DISTINCT p.*`
    // together with a `LEFT JOIN project_categories` (needed to de-duplicate the
    // fan-out from that join) and an ORDER BY containing expressions
    // (the CASE/current_amount switch and ts_rank(...)) that are NOT in the
    // select list. PostgreSQL rejects that combination outright:
    //   "ERROR: for SELECT DISTINCT, ORDER BY expressions must appear in select list"
    // meaning every single keyword search request threw a 500 error and never
    // returned results. Fixed by filtering on category via an EXISTS subquery
    // instead of a join — this removes the row fan-out entirely, so DISTINCT
    // is no longer needed and the ORDER BY is unrestricted.
    // Also added the missing ENDING_SOON case — previously any request with
    // sort=ENDING_SOON *and* a keyword silently fell back to relevance/newest
    // ordering instead of honoring the requested deadline-ascending sort.
    //
    // AUDIT FIX (Feature #16): minGoal/maxGoal are now filtered here (and in
    // the count query below) instead of in Java after pagination — see the
    // matching note on findForExplore() above for why that broke pagination.

    @Query(value = """
        SELECT p.*
        FROM   projects p
        WHERE  p.status = 'APPROVED'
        AND   (:categoryId IS NULL OR EXISTS (
                  SELECT 1 FROM project_categories pc
                  WHERE pc.project_id = p.id AND pc.category_id = :categoryId
              ))
        AND   (
                  p.search_vector @@ plainto_tsquery('english', :keyword)
               OR LOWER(p.title)             LIKE LOWER('%' || :keyword || '%')
               OR LOWER(p.short_description) LIKE LOWER('%' || :keyword || '%')
              )
        AND   (:minGoal IS NULL OR p.goal_amount >= :minGoal)
        AND   (:maxGoal IS NULL OR p.goal_amount <= :maxGoal)
        ORDER BY
            CASE WHEN :sort IN ('MOST_FUNDED','TRENDING')
                 THEN p.current_amount END DESC NULLS LAST,
            CASE WHEN :sort = 'ENDING_SOON'
                 THEN p.deadline END ASC NULLS LAST,
            ts_rank(p.search_vector, plainto_tsquery('english', :keyword)) DESC,
            p.created_at DESC
        """,
            countQuery = """
        SELECT COUNT(*)
        FROM   projects p
        WHERE  p.status = 'APPROVED'
        AND   (:categoryId IS NULL OR EXISTS (
                  SELECT 1 FROM project_categories pc
                  WHERE pc.project_id = p.id AND pc.category_id = :categoryId
              ))
        AND   (
                  p.search_vector @@ plainto_tsquery('english', :keyword)
               OR LOWER(p.title)             LIKE LOWER('%' || :keyword || '%')
               OR LOWER(p.short_description) LIKE LOWER('%' || :keyword || '%')
              )
        AND   (:minGoal IS NULL OR p.goal_amount >= :minGoal)
        AND   (:maxGoal IS NULL OR p.goal_amount <= :maxGoal)
        """,
            nativeQuery = true)
    Page<Project> searchWithFts(
            @Param("categoryId") Long   categoryId,
            @Param("keyword")    String keyword,
            @Param("sort")       String sort,
            @Param("minGoal")    Double minGoal,
            @Param("maxGoal")    Double maxGoal,
            Pageable             pageable
    );
}