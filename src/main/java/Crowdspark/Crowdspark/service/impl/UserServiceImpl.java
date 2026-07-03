package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.*;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.CloudinaryService;
import Crowdspark.Crowdspark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import Crowdspark.Crowdspark.service.EmailService;



@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final ModelMapper modelMapper;
    private final CloudinaryService cloudinaryService;
    private final EmailService emailService;

    // reg
    @Override
    public UserResponse register(RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername()))
            throw new AuthException("Username already exists");

        if (userRepository.existsByEmail(request.getEmail()))
            throw new AuthException("Email already exists");

        if (request.getPhoneNumber() != null &&
                userRepository.existsByPhoneNumber(request.getPhoneNumber()))
            throw new AuthException("Phone number already exists");

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

        User saved = userRepository.save(user);
        auditLogService.log(saved.getId(), "USER_REGISTERED", "USER", saved.getId());
        emailService.sendWelcomeEmail(saved.getEmail(), saved.getName());
        return mapToResponse(saved);
    }

    // get
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

    // prof
    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {

        User user = getById(userId);

        if (request.getBio() != null)                  user.setBio(request.getBio());
        if (request.getAbout() != null)                user.setAbout(request.getAbout());
        if (request.getGender() != null)               user.setGender(request.getGender());
        if (request.getDateOfBirth() != null)          user.setDateOfBirth(request.getDateOfBirth());
        if (request.getWebsiteUrl() != null)           user.setWebsiteUrl(request.getWebsiteUrl());
        if (request.getLinkedinUrl() != null)          user.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getInstagramUrl() != null)         user.setInstagramUrl(request.getInstagramUrl());
        if (request.getTwitterUrl() != null)           user.setTwitterUrl(request.getTwitterUrl());
        if (request.getAddressLine() != null)          user.setAddressLine(request.getAddressLine());
        if (request.getCity() != null)                 user.setCity(request.getCity());
        if (request.getState() != null)                user.setState(request.getState());
        if (request.getCountry() != null)              user.setCountry(request.getCountry());
        if (request.getPincode() != null)              user.setPincode(request.getPincode());
        if (request.getPlaceId() != null)              user.setPlaceId(request.getPlaceId());
        if (request.getLatitude() != null)             user.setLatitude(request.getLatitude());
        if (request.getLongitude() != null)            user.setLongitude(request.getLongitude());
        if (request.getProfession() != null)           user.setProfession(request.getProfession());
        if (request.getOrganization() != null)         user.setOrganization(request.getOrganization());
        if (request.getInterestedCategories() != null) user.setInterestedCategories(request.getInterestedCategories());

        User saved = userRepository.save(user);
        auditLogService.log(userId, "PROFILE_UPDATED", "USER", userId);

        return mapToResponse(saved);
    }

    // img
    @Override
    @Transactional
    public UserResponse updateProfileImage(Long userId, MultipartFile file) {

        User user = getById(userId);

        if (user.getProfileImagePublicId() != null)
            cloudinaryService.deleteFile(user.getProfileImagePublicId());

        Map<String, String> result = cloudinaryService.uploadFileWithDetails(file, "crowdspark/profiles");
        user.setProfileImageUrl(result.get("secure_url"));
        user.setProfileImagePublicId(result.get("public_id"));

        User saved = userRepository.save(user);
        auditLogService.log(userId, "PROFILE_IMAGE_UPDATED", "USER", userId);

        return mapToResponse(saved);
    }

    // bnr
    @Override
    @Transactional
    public UserResponse updateBannerImage(Long userId, MultipartFile file) {

        User user = getById(userId);

        if (user.getBannerImagePublicId() != null)
            cloudinaryService.deleteFile(user.getBannerImagePublicId());

        Map<String, String> result = cloudinaryService.uploadFileWithDetails(file, "crowdspark/banners");
        user.setBannerImageUrl(result.get("secure_url"));
        user.setBannerImagePublicId(result.get("public_id"));

        User saved = userRepository.save(user);
        auditLogService.log(userId, "BANNER_IMAGE_UPDATED", "USER", userId);

        return mapToResponse(saved);
    }

    // map
    private UserResponse mapToResponse(User user) {
        return modelMapper.map(user, UserResponse.class);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);

    }

    @Override
    public User save(User user) {
        return userRepository.save(user);
    }
    @Override
    public Optional<User> findByProviderAndProviderId(String provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId);
    }
    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
}