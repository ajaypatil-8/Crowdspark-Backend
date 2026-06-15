// src/main/java/Crowdspark/Crowdspark/repository/FcmTokenRepository.java

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findByUser_Id(Long userId);

    Optional<FcmToken> findByUser_IdAndToken(Long userId, String token);

    boolean existsByUser_IdAndToken(Long userId, String token);

    /** Hard-delete a stale/revoked token by its raw value */
    @Modifying
    @Query("DELETE FROM FcmToken f WHERE f.token = :token")
    void deleteByToken(@Param("token") String token);

    /** Remove all tokens for a user (e.g. on account deletion or unsubscribe-all) */
    @Modifying
    @Query("DELETE FROM FcmToken f WHERE f.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
