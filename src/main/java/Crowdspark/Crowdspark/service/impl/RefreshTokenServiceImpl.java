package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.RefreshToken;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.RefreshTokenRepository;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository repository;

    @Value("${jwt.refresh.expiration}")
    private long refreshExpiration;

    @Override
    public RefreshToken create(Long userId) {

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = org.apache.commons.codec.digest.DigestUtils.sha256Hex(rawToken);

        RefreshToken token = new RefreshToken();
        token.setToken(hashedToken); // store HASHED
        token.setUserId(userId);
        token.setRevoked(false);
        token.setExpiryDate(
                LocalDateTime.now().plus(Duration.ofMillis(refreshExpiration))
        );

        RefreshToken saved = repository.save(token);

        // IMPORTANT: return RAW token to client
        saved.setToken(rawToken);
        return saved;
    }


    @Override
    public RefreshToken validate(String token) {

        RefreshToken refreshToken = repository.findByToken(
                        org.apache.commons.codec.digest.DigestUtils.sha256Hex(token)
                )

                .orElseThrow(() -> new AuthException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new AuthException("Refresh token revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new AuthException("Refresh token expired");
        }

        return refreshToken;
    }

    @Override
    public void revoke(String token) {

        RefreshToken refreshToken = repository.findByToken(
                        org.apache.commons.codec.digest.DigestUtils.sha256Hex(token)
                )

                .orElseThrow(() -> new AuthException("Invalid refresh token"));

        refreshToken.setRevoked(true);
        repository.save(refreshToken);
    }

    @Override
    public void revokeAll(Long userId) {
        repository.revokeAllByUserId(userId);
    }
}
