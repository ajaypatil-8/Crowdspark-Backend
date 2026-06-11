// src/main/java/Crowdspark/Crowdspark/repository/ProjectMilestoneRepository.java

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.ProjectMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, Long> {

    /** All milestones for a project, ordered by sort_order then created_at */
    List<ProjectMilestone> findByProject_IdOrderBySortOrderAscCreatedAtAsc(Long projectId);

    Optional<ProjectMilestone> findByIdAndProject_Id(Long id, Long projectId);

    long countByProject_Id(Long projectId);

    /** Highest sort_order value in use — used to append new milestones at the end */
    @Query("SELECT COALESCE(MAX(m.sortOrder), -1) FROM ProjectMilestone m WHERE m.project.id = :projectId")
    Integer findMaxSortOrder(@Param("projectId") Long projectId);
}
