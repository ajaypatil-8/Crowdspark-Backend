package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.RegisterRequest;
import Crowdspark.Crowdspark.dto.UserResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final ModelMapper modelMapper;

    @Override
    public UserResponse register(RegisterRequest request) {

        // 1️⃣ Validate uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AuthException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Email already exists");
        }

        if (request.getPhoneNumber() != null &&
                userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AuthException("Phone number already exists");
        }

        // 2️⃣ Create user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setEnabled(true);
        user.setLocked(false);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setRoles(Set.of(Role.BACKER));



        User saved = userRepository.save(user);

        // 3️⃣ Audit log
        auditLogService.log(
                saved.getId(),
                "USER_REGISTERED",
                "USER",
                saved.getId()
        );

        // 4️⃣ Map to DTO
        return modelMapper.map(saved, UserResponse.class);
    }

    @Override
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AuthException("User not found"));
    }

    @Override
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("User not found"));
    }
}
