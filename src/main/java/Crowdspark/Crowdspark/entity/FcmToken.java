// src/main/java/Crowdspark/Crowdspark/entity/FcmToken.java

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
@Table(
    name = "fcm_tokens",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_fcm_user_token",
        columnNames = {"user_id", "token"}
    ),
    indexes = {
        @Index(name = "idx_fcm_tokens_user_id", columnList = "user_id"),
        @Index(name = "idx_fcm_tokens_token",   columnList = "token")
    }
)
public class FcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The raw FCM registration token from the browser */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String token;

    /** Optional browser/OS hint stored for debugging — not used in push logic */
    @Column(name = "device_hint", length = 100)
    private String deviceHint;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Refreshed each time the token is used for a push attempt */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
