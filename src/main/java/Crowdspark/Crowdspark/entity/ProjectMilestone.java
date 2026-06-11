// src/main/java/Crowdspark/Crowdspark/entity/ProjectMilestone.java

package Crowdspark.Crowdspark.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "project_milestones",
    indexes = {
        @Index(name = "idx_milestones_project_id",    columnList = "project_id"),
        @Index(name = "idx_milestones_project_order", columnList = "project_id, sort_order")
    }
)
public class ProjectMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;


    @Column(name = "target_amount")
    private Double targetAmount;

    /** Position in the ordered list (0-indexed). Creator can reorder. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * Null = PENDING.
     * Non-null = COMPLETED — set when creator calls the complete endpoint.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Convenience — not persisted
    @Transient
    public boolean isCompleted() {
        return completedAt != null;
    }
}
