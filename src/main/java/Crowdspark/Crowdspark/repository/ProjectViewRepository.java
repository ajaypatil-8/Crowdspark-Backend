// src/main/java/Crowdspark/Crowdspark/repository/ProjectViewRepository.java
package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.ProjectView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProjectViewRepository extends JpaRepository<ProjectView, Long> {

    Optional<ProjectView> findByProject_IdAndViewDate(Long projectId, LocalDate date);

    /** Last N days of view data for charts */
    List<ProjectView> findByProject_IdAndViewDateBetweenOrderByViewDateAsc(
            Long projectId, LocalDate from, LocalDate to);

    /** Total all-time views */
    @Query("SELECT COALESCE(SUM(v.viewCount), 0) FROM ProjectView v WHERE v.project.id = :projectId")
    Long sumViewsByProject(@Param("projectId") Long projectId);

    /** Total all-time unique visitors */
    @Query("SELECT COALESCE(SUM(v.uniqueCount), 0) FROM ProjectView v WHERE v.project.id = :projectId")
    Long sumUniqueByProject(@Param("projectId") Long projectId);

    /** Upsert: increment today's view count by 1 */
    @Modifying
    @Query(value = """
        INSERT INTO project_views (project_id, view_date, view_count, unique_count)
        VALUES (:projectId, CURRENT_DATE, 1, 0)
        ON CONFLICT (project_id, view_date)
        DO UPDATE SET view_count = project_views.view_count + 1
        """, nativeQuery = true)
    void incrementViewCount(@Param("projectId") Long projectId);

    /**
     * Upsert: flush a given day's unique count from Redis.
     *
     * BUG FIX: this previously wrote to CURRENT_DATE instead of the actual
     * date being flushed. flushUniquesToDb() runs at midnight and flushes
     * YESTERDAY's HyperLogLog data — but CURRENT_DATE is evaluated by the
     * database at query time, which by then is already the new day. That
     * silently created a bogus row for *today* (view_count=0, unique_count=
     * <yesterday's number>) every single night, while yesterday's real row
     * (with its correct view_count from actual traffic) permanently kept
     * unique_count=0. Now the target date is passed explicitly so it always
     * matches the date the HLL data actually belongs to.
     */
    @Modifying
    @Query(value = """
        INSERT INTO project_views (project_id, view_date, view_count, unique_count)
        VALUES (:projectId, :viewDate, 0, :uniqueCount)
        ON CONFLICT (project_id, view_date)
        DO UPDATE SET unique_count = :uniqueCount
        """, nativeQuery = true)
    void updateUniqueCount(@Param("projectId") Long projectId,
                           @Param("viewDate") LocalDate viewDate,
                           @Param("uniqueCount") Long uniqueCount);
}