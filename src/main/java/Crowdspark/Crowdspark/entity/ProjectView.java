// src/main/java/Crowdspark/Crowdspark/entity/ProjectView.java
package Crowdspark.Crowdspark.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "project_views",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_project_views_date",
                columnNames = {"project_id", "view_date"}),
        indexes = {
                @Index(name = "idx_project_views_project_id", columnList = "project_id"),
                @Index(name = "idx_project_views_date", columnList = "project_id, view_date DESC")
        })
public class ProjectView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "view_date", nullable = false)
    private LocalDate viewDate;

    @Column(nullable = false)
    private Long viewCount = 0L;

    /** Estimated unique visitors — flushed from Redis HyperLogLog nightly */
    @Column(nullable = false)
    private Long uniqueCount = 0L;
}
