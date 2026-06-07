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

    /** Upsert: flush today's unique count from Redis */
    @Modifying
    @Query(value = """
        INSERT INTO project_views (project_id, view_date, view_count, unique_count)
        VALUES (:projectId, CURRENT_DATE, 0, :uniqueCount)
        ON CONFLICT (project_id, view_date)
        DO UPDATE SET unique_count = :uniqueCount
        """, nativeQuery = true)
    void updateUniqueCount(@Param("projectId") Long projectId,
                           @Param("uniqueCount") Long uniqueCount);
}
