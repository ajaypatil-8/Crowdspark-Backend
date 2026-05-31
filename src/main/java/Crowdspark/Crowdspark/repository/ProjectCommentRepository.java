// src/main/java/Crowdspark/Crowdspark/repository/ProjectCommentRepository.java
package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.ProjectComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectCommentRepository extends JpaRepository<ProjectComment, Long> {

    /** Top-level comments only (no replies), newest first, paginated */
    @Query("""
        SELECT c FROM ProjectComment c
        WHERE c.project.id = :projectId
          AND c.parentComment IS NULL
        ORDER BY c.createdAt DESC
    """)
    Page<ProjectComment> findTopLevelByProjectId(
            @Param("projectId") Long projectId, Pageable pageable);

    /** Total comment count for a project (including replies, excluding deleted) */
    @Query("""
        SELECT COUNT(c) FROM ProjectComment c
        WHERE c.project.id = :projectId AND c.deleted = false
    """)
    long countActiveByProjectId(@Param("projectId") Long projectId);
}
