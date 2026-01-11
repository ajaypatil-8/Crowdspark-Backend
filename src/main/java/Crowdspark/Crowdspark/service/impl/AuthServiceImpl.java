package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    public User login(String identifier, String password) {

        Optional<User> userOptional = userRepository.findByUsername(identifier);

        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByEmail(identifier);
        }

        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByPhoneNumber(identifier);
        }

        User user = userOptional.orElseThrow(
                () -> new AuthException("Invalid credentials")
        );

        if (!user.isEnabled() || user.isLocked()) {
            throw new AuthException("Invalid credentials");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new AuthException("Invalid credentials");
        }

        // 🔐 Audit log for login
        auditLogService.log(
                user.getId(),
                "LOGIN_SUCCESS",
                "USER",
                user.getId()
        );

        return user;
    }
}
