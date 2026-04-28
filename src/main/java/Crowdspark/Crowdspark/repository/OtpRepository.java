package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findByEmail(String email);

    @Transactional
    void deleteByEmail(String email);
}
