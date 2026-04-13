package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.KycStatusResponse;
import Crowdspark.Crowdspark.dto.KycSubmitRequest;
import Crowdspark.Crowdspark.entity.KycDocument;
import Crowdspark.Crowdspark.entity.OtpVerification;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.KycStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.KycDocumentRepository;
import Crowdspark.Crowdspark.repository.OtpRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.EmailService;
import Crowdspark.Crowdspark.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public String sendOtp(Long userId) {

        User user = getUser(userId);

        // Already fully approved — no need
        if (user.getKycStatus() == KycStatus.APPROVED) {
            throw new AuthException("You are already a verified creator");
        }


        String otp = String.valueOf(100000 + new Random().nextInt(900000));


        otpRepository.findByEmail(user.getEmail())
                .ifPresent(otpRepository::delete);

        // Save new OTP — expires in 10 minutes
        OtpVerification otpVerification = new OtpVerification();
        otpVerification.setEmail(user.getEmail());
        otpVerification.setOtp(otp);
        otpVerification.setExpiryTime(LocalDateTime.now().plusMinutes(10));
        otpRepository.save(otpVerification);

        // Send email
        emailService.sendOtpEmail(user.getEmail(), user.getName(), otp);

        auditLogService.log(userId, "CREATOR_OTP_SENT", "USER", userId);

        return "OTP sent to " + user.getEmail() + ". Valid for 10 minutes.";
    }

    @Override
    @Transactional
    public String verifyOtp(Long userId, String otpInput) {

        User user = getUser(userId);

        if (user.getKycStatus() == KycStatus.APPROVED) {
            throw new AuthException("You are already a verified creator");
        }

        // Find OTP
        OtpVerification otp = otpRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new AuthException("OTP not found. Please request a new OTP"));

        // Check expiry
        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
            otpRepository.delete(otp);
            throw new AuthException("OTP has expired. Please request a new one");
        }

        // Check match
        if (!otp.getOtp().equals(otpInput)) {
            throw new AuthException("Invalid OTP. Please try again");
        }


        user.addRole(Role.CREATOR);
        user.setKycStatus(KycStatus.PENDING_SUBMISSION);
        userRepository.save(user);

        // Delete used OTP
        otpRepository.delete(otp);

        auditLogService.log(userId, "CREATOR_OTP_VERIFIED", "USER", userId);

        return "OTP verified successfully. Please submit your KYC documents to complete creator setup.";
    }


    @Override
    @Transactional
    public KycStatusResponse submitKyc(Long userId, KycSubmitRequest request) {

        User user = getUser(userId);

        // Must have verified OTP first
        if (!user.hasRole(Role.CREATOR)) {
            throw new AuthException("Please verify OTP first before submitting KYC");
        }

        if (user.getKycStatus() == KycStatus.APPROVED) {
            throw new AuthException("Your KYC is already approved");
        }

        if (user.getKycStatus() == KycStatus.PENDING_APPROVAL) {
            throw new AuthException("Your KYC is already submitted and under review");
        }

        if (user.getKycStatus() == KycStatus.NOT_SUBMITTED) {
            throw new AuthException("Please verify OTP first before submitting KYC");
        }


        KycDocument kyc = kycDocumentRepository.findByUserId(userId)
                .orElse(new KycDocument());

        kyc.setUserId(userId);
        kyc.setKycStatus(KycStatus.PENDING_APPROVAL);
        kyc.setRejectionReason(null);  // clear any old rejection reason

        // PAN
        kyc.setPanNumber(request.getPanNumber());
        kyc.setPanCardImageUrl(request.getPanCardImageUrl());
        kyc.setPanCardImagePublicId(request.getPanCardImagePublicId());

        // Aadhaar
        kyc.setAadhaarNumber(request.getAadhaarNumber());
        kyc.setAadhaarFrontImageUrl(request.getAadhaarFrontImageUrl());
        kyc.setAadhaarFrontPublicId(request.getAadhaarFrontPublicId());
        kyc.setAadhaarBackImageUrl(request.getAadhaarBackImageUrl());
        kyc.setAadhaarBackPublicId(request.getAadhaarBackPublicId());

        // Bank
        kyc.setBankAccountHolderName(request.getBankAccountHolderName());
        kyc.setBankAccountNumber(request.getBankAccountNumber());
        kyc.setBankIfscCode(request.getBankIfscCode());
        kyc.setBankName(request.getBankName());
        kyc.setBankBranchName(request.getBankBranchName());

        // UPI
        kyc.setUpiId(request.getUpiId());

        kycDocumentRepository.save(kyc);

        // Update user kycStatus
        user.setKycStatus(KycStatus.PENDING_APPROVAL);
        userRepository.save(user);

        auditLogService.log(userId, "KYC_SUBMITTED", "KYC_DOCUMENT", kyc.getId());

        return mapToResponse(user, kyc);
    }


    @Override
    public KycStatusResponse getMyKycStatus(Long userId) {
        User user = getUser(userId);
        KycDocument kyc = kycDocumentRepository.findByUserId(userId)
                .orElse(null);
        return mapToResponse(user, kyc);
    }


    @Override
    public List<KycStatusResponse> getPendingKyc() {
        return kycDocumentRepository.findByKycStatus(KycStatus.PENDING_APPROVAL)
                .stream()
                .map(kyc -> {
                    User user = getUser(kyc.getUserId());
                    return mapToResponse(user, kyc);
                })
                .collect(Collectors.toList());
    }


    @Override
    @Transactional
    public KycStatusResponse approveKyc(Long userId, Long adminId) {

        User user = getUser(userId);
        KycDocument kyc = getKycDocument(userId);

        if (kyc.getKycStatus() != KycStatus.PENDING_APPROVAL) {
            throw new AuthException("KYC is not in pending approval state");
        }

        kyc.setKycStatus(KycStatus.APPROVED);
        kyc.setReviewedAt(LocalDateTime.now());
        kyc.setReviewedByAdminId(adminId);
        kyc.setRejectionReason(null);
        kycDocumentRepository.save(kyc);

        user.setKycStatus(KycStatus.APPROVED);
        user.setKycVerified(true);
        // Copy bank/payment info from KYC doc to user for easy access in UserResponse
        user.setBankName(kyc.getBankName());
        user.setBankIfscCode(kyc.getBankIfscCode());
        user.setUpiId(kyc.getUpiId());
        if (kyc.getBankAccountNumber() != null && kyc.getBankAccountNumber().length() >= 4) {
            user.setMaskedBankAccount("****" +
                    kyc.getBankAccountNumber().substring(kyc.getBankAccountNumber().length() - 4));
        }
        userRepository.save(user);

        // Notify user via email
        emailService.sendSimpleEmail(
                user.getEmail(),
                "CrowdSpark — KYC Approved! 🎉",
                "Hi " + user.getName() + ",\n\n" +
                        "Congratulations! Your KYC has been approved. " +
                        "You can now create campaigns on CrowdSpark.\n\n" +
                        "Team CrowdSpark"
        );

        auditLogService.log(adminId, "KYC_APPROVED", "KYC_DOCUMENT", kyc.getId());

        return mapToResponse(user, kyc);
    }


    @Override
    @Transactional
    public KycStatusResponse rejectKyc(Long userId, Long adminId, String reason) {

        User user = getUser(userId);
        KycDocument kyc = getKycDocument(userId);

        if (kyc.getKycStatus() != KycStatus.PENDING_APPROVAL) {
            throw new AuthException("KYC is not in pending approval state");
        }

        if (reason == null || reason.isBlank()) {
            throw new AuthException("Rejection reason is required");
        }

        kyc.setKycStatus(KycStatus.REJECTED);
        kyc.setRejectionReason(reason);
        kyc.setReviewedAt(LocalDateTime.now());
        kyc.setReviewedByAdminId(adminId);
        kycDocumentRepository.save(kyc);

        // Set back to PENDING_SUBMISSION so user can resubmit
        user.setKycStatus(KycStatus.PENDING_SUBMISSION);
        user.setKycVerified(false);
        userRepository.save(user);

        // Notify user via email
        emailService.sendSimpleEmail(
                user.getEmail(),
                "CrowdSpark — KYC Rejected",
                "Hi " + user.getName() + ",\n\n" +
                        "Unfortunately your KYC submission was rejected.\n\n" +
                        "Reason: " + reason + "\n\n" +
                        "Please resubmit your documents with the correct information.\n\n" +
                        "Team CrowdSpark"
        );

        auditLogService.log(adminId, "KYC_REJECTED", "KYC_DOCUMENT", kyc.getId());

        return mapToResponse(user, kyc);
    }


    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found"));
    }

    private KycDocument getKycDocument(Long userId) {
        return kycDocumentRepository.findByUserId(userId)
                .orElseThrow(() -> new AuthException("KYC document not found for this user"));
    }

    private KycStatusResponse mapToResponse(User user, KycDocument kyc) {
        KycStatusResponse response = new KycStatusResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setKycStatus(user.getKycStatus());

        if (kyc != null) {
            response.setRejectionReason(kyc.getRejectionReason());
            response.setPanNumber(kyc.getPanNumber());
            response.setPanCardImageUrl(kyc.getPanCardImageUrl());
            response.setAadhaarNumber(kyc.getAadhaarNumber());
            response.setAadhaarFrontImageUrl(kyc.getAadhaarFrontImageUrl());
            response.setAadhaarBackImageUrl(kyc.getAadhaarBackImageUrl());
            response.setBankName(kyc.getBankName());
            response.setBankIfscCode(kyc.getBankIfscCode());
            response.setUpiId(kyc.getUpiId());
            response.setSubmittedAt(kyc.getSubmittedAt());
            response.setReviewedAt(kyc.getReviewedAt());

            // Mask bank account
            if (kyc.getBankAccountNumber() != null && kyc.getBankAccountNumber().length() >= 4) {
                response.setMaskedBankAccount("****" +
                        kyc.getBankAccountNumber()
                                .substring(kyc.getBankAccountNumber().length() - 4));
            }
        }

        return response;
    }
}