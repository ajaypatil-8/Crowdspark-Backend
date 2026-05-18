package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.ContactMessageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "contact_messages", indexes = {
        @Index(name = "idx_contact_messages_status", columnList = "status"),
        @Index(name = "idx_contact_messages_created_at", columnList = "created_at"),
        @Index(name = "idx_contact_messages_email", columnList = "email")
})
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(nullable = false, length = 80)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactMessageStatus status = ContactMessageStatus.NEW;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    @Column(name = "reply_subject", length = 180)
    private String replySubject;

    @Column(name = "reply_message", columnDefinition = "TEXT")
    private String replyMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replied_by_id")
    private User repliedBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}