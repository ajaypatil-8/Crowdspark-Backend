package Crowdspark.Crowdspark.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reward_tiers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RewardTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double minimumAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
}
