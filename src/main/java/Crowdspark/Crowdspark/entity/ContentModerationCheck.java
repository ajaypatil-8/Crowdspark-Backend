// src/main/java/Crowdspark/Crowdspark/entity/ContentModerationCheck.java
// Feature #45 — AI Content Moderation
//
// Generic across content types (contentType + contentId) rather than a
// separate table per type, unlike ProjectFraudCheck/KycDocumentAiCheck's
// dedicated one-table-per-check-purpose approach from #43/#44. The
// difference: this feature covers two content types (projects AND
// comments) with the exact same check logic and result shape, and comment
// volume can grow much larger than project or KYC submissions, so one
// lightweight polymorphic-by-convention table scales better than two near-
// duplicate ones. No FK constraint on contentId for exactly that reason —
// it can point at either projects.id or project_comments.id depending on
// contentType.

package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.ContentType;
import Crowdspark.Crowdspark.entity.type.ModerationStatus;
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
@Table(name = "content_moderation_checks",
        indexes = {
                @Index(name = "idx_moderation_content", columnList = "content_type, content_id"),
                @Index(name = "idx_moderation_status", columnList = "status")
        })
public class ContentModerationCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModerationStatus status = ModerationStatus.PENDING;

    /** "spam" / "hate_speech" / "misleading" / "none", null until scanned. */
    private String category;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    private String model;
    private LocalDateTime checkedAt;

    /** Admin has looked at this flag and made a decision (restore/confirm). */
    @Column(nullable = false)
    private boolean resolvedByAdmin = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
