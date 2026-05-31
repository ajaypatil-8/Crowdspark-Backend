// src/main/java/Crowdspark/Crowdspark/entity/SavedProject.java
package Crowdspark.Crowdspark.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "saved_projects",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_saved_user_project",
                columnNames = {"user_id", "project_id"}
        ),
        indexes = {
                @Index(name = "idx_saved_projects_user_id",
                        columnList = "user_id"),
                @Index(name = "idx_saved_projects_project_id",
                        columnList = "project_id")
        })
public class SavedProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime savedAt;
}
