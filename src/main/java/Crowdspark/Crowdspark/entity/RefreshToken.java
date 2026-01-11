package Crowdspark.Crowdspark.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import org.apache.commons.codec.digest.DigestUtils;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    private boolean revoked = false;
}
