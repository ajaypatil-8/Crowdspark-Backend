package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.VerifyOtpRequest;
import Crowdspark.Crowdspark.entity.OtpVerification;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.Role;
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

    // SEND OTP
    @Override
    @Transactional
    public String sendOtp() {

        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getEmail() == null) {
            throw new RuntimeException("Email not found for this user");
        }

        if (user.getRoles().contains(Role.CREATOR)) {
            throw new RuntimeException("You are already a creator");
        }

        // generate OTP
        String otp = String.valueOf(new Random().nextInt(900000) + 100000);


        // delete old OTP
        otpRepository.deleteByEmail(user.getEmail());

        // save new OTP
        OtpVerification otpEntity = OtpVerification.builder()
                .email(user.getEmail())
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();

        otpRepository.save(otpEntity);

        // send email
        emailService.sendOtpEmail(user.getEmail(), otp);

        return "OTP sent to your registered email";
    }

    // VERIFY OTP
    @Override
    @Transactional
    public String verifyOtp(VerifyOtpRequest request) {

        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        OtpVerification otpEntity = otpRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new RuntimeException("OTP not found"));

        if (otpEntity.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        if (!otpEntity.getOtp().equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }

        // ADD CREATOR ROLE
        user.getRoles().add(Role.CREATOR);
        userRepository.save(user);

        // delete otp
        otpRepository.deleteByEmail(user.getEmail());

        return "Creator role activated successfully 🚀";
    }
}
