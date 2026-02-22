package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.*;
import Crowdspark.Crowdspark.entity.User;

public interface UserService {

    // Registration
    UserResponse register(RegisterRequest request);

    // Getters
    User getById(Long id);
    User getByUsername(String username);

    // Profile update (bio, about, location, social links etc.)
    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    // Image uploads — frontend uploads to Cloudinary first, sends URL here
    UserResponse updateProfileImage(Long userId, String imageUrl, String publicId);
    UserResponse updateBannerImage(Long userId, String imageUrl, String publicId);

    // Backer → Creator upgrade (requires OTP + documents + payout info)
    UserResponse upgradeToCreator(Long userId, CreatorUpgradeRequest request);

    // Get public profile
    UserResponse getProfile(Long userId);
}