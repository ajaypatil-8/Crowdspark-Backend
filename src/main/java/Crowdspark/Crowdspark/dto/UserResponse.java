package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.AccountStatus;
import Crowdspark.Crowdspark.entity.type.Gender;
import Crowdspark.Crowdspark.entity.type.KycStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {


    private Long id;
    private String name;
    private String username;
    private String email;
    private String phoneNumber;

    private Set<Role> roles;
    private AccountStatus accountStatus;

    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean kycVerified;

    private LocalDateTime createdAt;


    private String profileImageUrl;
    private String bannerImageUrl;


    private String bio;
    private String about;
    private Gender gender;
    private LocalDate dateOfBirth;

    private String websiteUrl;
    private String linkedinUrl;
    private String instagramUrl;
    private String twitterUrl;

    private String addressLine;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private Double latitude;
    private Double longitude;


    private String profession;
    private String organization;
    private Set<String> interestedCategories;


    private String upiId;
    private String bankName;
    private String maskedBankAccount;
    private String bankIfscCode;


    private Integer totalProjectsBacked;
    private Double totalAmountBacked;
    private Integer totalProjectsCreated;
    private Double totalFundsRaised;
    private KycStatus kycStatus;
}