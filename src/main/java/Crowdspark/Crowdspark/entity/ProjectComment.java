// src/main/java/Crowdspark/Crowdspark/entity/ProjectComment.java
package Crowdspark.Crowdspark.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "project_comments",
        indexes = {
                @Index(name = "idx_comments_project_id", columnList = "project_id"),
                @Index(name = "idx_comments_parent_id",  columnList = "parent_comment_id"),
                @Index(name = "idx_comments_author_id",  columnList = "author_id")
        })
public class ProjectComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Null = top-level comment. Non-null = reply to another comment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private ProjectComment parentComment;

    /** One level of replies only (replies to top-level comments) */
    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ProjectComment> replies = new ArrayList<>();

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Soft delete — content replaced with "[deleted]", author retained */
    @Column(nullable = false)
    private boolean deleted = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
