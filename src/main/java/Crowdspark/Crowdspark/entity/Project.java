package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // BASIC INFO
    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 300)
    private String shortDescription;

    @Column(nullable = false, length = 10000)
    private String fullDescription;

    @Column(nullable = false)
    private String location;

    // MONEY
    @Column(nullable = false)
    private Double goalAmount;

    @Column(nullable = false)
    private Double currentAmount = 0.0;

    // DEADLINE
    @Column(nullable = false)
    private LocalDateTime deadline;

    // STATUS
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    // ADMIN
    private String rejectionReason;
    private LocalDateTime approvedAt;

    // CREATOR RELATION
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    // CREATED TIME
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // CATEGORIES
    @ManyToMany
    @JoinTable(
            name = "project_categories",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories = new ArrayList<>();

    // ALL MEDIA (images + videos)
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectMedia> media = new ArrayList<>();
}
