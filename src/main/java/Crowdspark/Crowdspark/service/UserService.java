package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.*;
import Crowdspark.Crowdspark.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {


    UserResponse register(RegisterRequest request);


    User getById(Long id);
    User getByUsername(String username);
    UserResponse getProfile(Long userId);


    UserResponse updateProfile(Long userId, UpdateProfileRequest request);


    UserResponse updateProfileImage(Long userId, MultipartFile file);
    UserResponse updateBannerImage(Long userId, MultipartFile file);


    UserResponse upgradeToCreator(Long userId, CreatorUpgradeRequest request);
}