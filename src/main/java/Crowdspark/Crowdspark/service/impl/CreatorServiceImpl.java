package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.VerifyOtpRequest;
import Crowdspark.Crowdspark.entity.OtpVerification;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.entity.type.KycStatus;
import Crowdspark.Crowdspark.repository.OtpRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.CreatorService;
import Crowdspark.Crowdspark.service.EmailService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class CreatorServiceImpl implements CreatorService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final EmailService emailService;

    private User getCurrentUser() {
        String userIdStr = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal()
                .toString();
        Long userId = Long.parseLong(userIdStr);
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // send
    @Override
    @Transactional
    public String sendOtp() {
        User user = getCurrentUser();

        if (user.getEmail() == null)
            throw new RuntimeException("Email not found for this user");

        if (user.getRoles().contains(Role.CREATOR) && user.getKycStatus() == KycStatus.APPROVED)
            throw new RuntimeException("You are already a verified creator");

        String otp = String.valueOf(new Random().nextInt(900000) + 100000);

        otpRepository.deleteByEmail(user.getEmail());

        OtpVerification otpEntity = OtpVerification.builder()
                .email(user.getEmail())
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();

        otpRepository.save(otpEntity);

        // fix: pass name as 2nd param
        emailService.sendOtpEmail(user.getEmail(), user.getName(), otp);

        return "OTP sent to your registered email";
    }

    // verify
    @Override
    @Transactional
    public String verifyOtp(VerifyOtpRequest request) {
        User user = getCurrentUser();

        OtpVerification otpEntity = otpRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new RuntimeException("OTP not found"));

        if (otpEntity.getExpiryTime().isBefore(LocalDateTime.now()))
            throw new RuntimeException("OTP expired");

        if (!otpEntity.getOtp().equals(request.getOtp()))
            throw new RuntimeException("Invalid OTP");

        user.getRoles().add(Role.CREATOR);
        user.setKycStatus(KycStatus.PENDING_SUBMISSION);
        userRepository.save(user);

        otpRepository.deleteByEmail(user.getEmail());

        return "OTP verified. Please submit your KYC documents.";
    }
}