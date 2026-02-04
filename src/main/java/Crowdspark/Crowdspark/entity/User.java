package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String username;

    @Column(unique = true)
    private String email;        // nullable ✔

    @Column(unique = true)
    private String phoneNumber;  // nullable ✔

    private String password;     // nullable ✔ (OAuth / OTP)

    private String provider;     // LOCAL, OTP, GOOGLE
    private String providerId;

    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean isEnabled = true;
    private boolean isLocked = false;


    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    private Set<Role> roles = new HashSet<>();

}

