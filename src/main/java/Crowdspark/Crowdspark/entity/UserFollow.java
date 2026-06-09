// src/main/java/Crowdspark/Crowdspark/entity/UserFollow.java
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
@Table(name = "user_follows",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_follow",
                columnNames = {"follower_id", "following_id"}),
        indexes = {
                @Index(name = "idx_follows_follower_id",  columnList = "follower_id"),
                @Index(name = "idx_follows_following_id", columnList = "following_id")
        })
public class UserFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who is following */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    /** The creator being followed */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime followedAt;
}
