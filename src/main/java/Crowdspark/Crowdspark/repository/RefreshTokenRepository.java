package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.RefreshToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query("""
        UPDATE RefreshToken r
        SET r.revoked = true
        WHERE r.userId = :userId
    """)
    void revokeAllByUserId(@Param("userId") Long userId);
}
