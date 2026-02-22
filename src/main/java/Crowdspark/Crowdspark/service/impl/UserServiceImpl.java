package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.*;
import Crowdspark.Crowdspark.entity.OtpVerification;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.OtpRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final ModelMapper modelMapper;
    private final OtpRepository otpRepository;

    // ─────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────
    @Override
    public UserResponse register(RegisterRequest request) {

        // Validate uniqueness
        if (userRepository.existsByUsername(request.getUsername()))
            throw new AuthException("Username already exists");

        if (userRepository.existsByEmail(request.getEmail()))
            throw new AuthException("Email already exists");

        if (request.getPhoneNumber() != null &&
                userRepository.existsByPhoneNumber(request.getPhoneNumber()))
            throw new AuthException("Phone number already exists");

        // Build user
        User user = new User();
        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(true);
        user.setLocked(false);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setRoles(Set.of(Role.BACKER));

        // All creator / profile fields are null by default — filled later

        User saved = userRepository.save(user);

        auditLogService.log(saved.getId(), "USER_REGISTERED", "USER", saved.getId());

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // GETTERS
    // ─────────────────────────────────────────
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

    @Override
    public UserResponse getProfile(Long userId) {
        return mapToResponse(getById(userId));
    }

    // ─────────────────────────────────────────
    // UPDATE PROFILE (Common for Backer + Creator)
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {

        User user = getById(userId);

        if (request.getBio() != null)               user.setBio(request.getBio());
        if (request.getAbout() != null)             user.setAbout(request.getAbout());
        if (request.getGender() != null)            user.setGender(request.getGender());
        if (request.getDateOfBirth() != null)       user.setDateOfBirth(request.getDateOfBirth());
        if (request.getWebsiteUrl() != null)        user.setWebsiteUrl(request.getWebsiteUrl());
        if (request.getLinkedinUrl() != null)       user.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getInstagramUrl() != null)      user.setInstagramUrl(request.getInstagramUrl());
        if (request.getTwitterUrl() != null)        user.setTwitterUrl(request.getTwitterUrl());

        // Location
        if (request.getAddressLine() != null)       user.setAddressLine(request.getAddressLine());
        if (request.getCity() != null)              user.setCity(request.getCity());
        if (request.getState() != null)             user.setState(request.getState());
        if (request.getCountry() != null)           user.setCountry(request.getCountry());
        if (request.getPincode() != null)           user.setPincode(request.getPincode());
        if (request.getPlaceId() != null)           user.setPlaceId(request.getPlaceId());
        if (request.getLatitude() != null)          user.setLatitude(request.getLatitude());
        if (request.getLongitude() != null)         user.setLongitude(request.getLongitude());

        // Creator professional info (no harm storing even for backers if they fill it)
        if (request.getProfession() != null)            user.setProfession(request.getProfession());
        if (request.getOrganization() != null)          user.setOrganization(request.getOrganization());
        if (request.getInterestedCategories() != null)  user.setInterestedCategories(request.getInterestedCategories());

        User saved = userRepository.save(user);
        auditLogService.log(userId, "PROFILE_UPDATED", "USER", userId);

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // UPDATE PROFILE IMAGE
    // Frontend uploads image to Cloudinary → sends URL + publicId here
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public UserResponse updateProfileImage(Long userId, String imageUrl, String publicId) {
        User user = getById(userId);
        user.setProfileImageUrl(imageUrl);
        user.setProfileImagePublicId(publicId);
        User saved = userRepository.save(user);
        auditLogService.log(userId, "PROFILE_IMAGE_UPDATED", "USER", userId);
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // UPDATE BANNER IMAGE
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public UserResponse updateBannerImage(Long userId, String imageUrl, String publicId) {
        User user = getById(userId);
        user.setBannerImageUrl(imageUrl);
        user.setBannerImagePublicId(publicId);
        User saved = userRepository.save(user);
        auditLogService.log(userId, "BANNER_IMAGE_UPDATED", "USER", userId);
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────
    // UPGRADE BACKER → CREATOR
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public UserResponse upgradeToCreator(Long userId, CreatorUpgradeRequest request) {

        User user = getById(userId);

        // 1️⃣ Already a creator?
        if (user.hasRole(Role.CREATOR)) {
            throw new AuthException("User is already a creator");
        }

        // 2️⃣ Verify OTP
        OtpVerification otp = otpRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new AuthException("OTP not found. Please request a new OTP"));

        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new AuthException("OTP has expired. Please request a new one");
        }

        if (!otp.getOtp().equals(request.getOtp())) {
            throw new AuthException("Invalid OTP");
        }

        // 3️⃣ KYC Documents (Cloudinary URLs — images already uploaded by frontend)
        user.setPanNumber(request.getPanNumber());
        user.setPanCardImageUrl(request.getPanCardImageUrl());
        user.setPanCardImagePublicId(request.getPanCardImagePublicId());

        user.setAadhaarNumber(request.getAadhaarNumber());
        user.setAadhaarFrontImageUrl(request.getAadhaarFrontImageUrl());
        user.setAadhaarFrontPublicId(request.getAadhaarFrontPublicId());
        user.setAadhaarBackImageUrl(request.getAadhaarBackImageUrl());
        user.setAadhaarBackPublicId(request.getAadhaarBackPublicId());

        user.setKycSubmittedAt(LocalDateTime.now());

        // 4️⃣ Bank details
        user.setBankAccountHolderName(request.getBankAccountHolderName());
        user.setBankAccountNumber(request.getBankAccountNumber());
        user.setBankIfscCode(request.getBankIfscCode());
        user.setBankName(request.getBankName());
        user.setBankBranchName(request.getBankBranchName());

        // 5️⃣ UPI
        user.setUpiId(request.getUpiId());

        // 6️⃣ Add CREATOR role (keeps BACKER role too — user can still back projects)
        user.addRole(Role.CREATOR);

        // 7️⃣ KYC pending admin verification (isKycVerified stays false until admin approves)

        // 8️⃣ Delete used OTP
        otpRepository.delete(otp);

        User saved = userRepository.save(user);

        auditLogService.log(userId, "CREATOR_UPGRADE_REQUESTED", "USER", userId);

        return mapToResponse(saved);
    }


    private UserResponse mapToResponse(User user) {
        UserResponse response = modelMapper.map(user, UserResponse.class);

        // Mask bank account — show only last 4 digits
        if (user.getBankAccountNumber() != null && user.getBankAccountNumber().length() >= 4) {
            String masked = "****" + user.getBankAccountNumber()
                    .substring(user.getBankAccountNumber().length() - 4);
            response.setMaskedBankAccount(masked);
        }

        return response;
    }
}