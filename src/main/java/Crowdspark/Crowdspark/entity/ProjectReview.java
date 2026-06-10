// src/main/java/Crowdspark/Crowdspark/entity/ProjectReview.java

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
    name = "project_reviews",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_review_user_project",
        columnNames = {"project_id", "reviewer_id"}
    ),
    indexes = {
        @Index(name = "idx_reviews_project_id",  columnList = "project_id"),
        @Index(name = "idx_reviews_reviewer_id", columnList = "reviewer_id"),
        @Index(name = "idx_reviews_rating",      columnList = "project_id, rating")
    }
)
public class ProjectReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    /** Star rating 1–5 */
    @Column(nullable = false)
    private Integer rating;

    /** Optional short headline */
    @Column(length = 255)
    private String title;

    /** Full review text — optional */
    @Column(columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
