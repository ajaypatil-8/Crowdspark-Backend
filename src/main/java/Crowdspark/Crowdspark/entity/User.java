package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.AccountStatus;
import Crowdspark.Crowdspark.entity.type.Gender;
import Crowdspark.Crowdspark.entity.type.KycStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users", indexes = {
        @Index(name = "idx_users_account_status", columnList = "account_status"),
        @Index(name = "idx_users_kyc_status",     columnList = "kyc_status")
})
public class User {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private String name;

    @Column(unique = true)
    private String username;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phoneNumber;

    private String password;

    private String provider;
    private String providerId;


    private boolean emailVerified;
    private boolean phoneVerified;
    @Column(name = "kyc_verified", columnDefinition = "boolean default false")
    private boolean kycVerified = false;
    private boolean isEnabled = true;
    private boolean isLocked = false;


    @Column(name = "totp_secret", length = 64)
    private String totpSecret;


    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus kycStatus = KycStatus.NOT_SUBMITTED;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;


    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    private Set<Role> roles = new HashSet<>();


    private String profileImageUrl;
    private String profileImagePublicId;
    private String bannerImageUrl;
    private String bannerImagePublicId;


    private String bio;

    @Column(length = 1000)
    private String about;

    @Enumerated(EnumType.STRING)
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
    private String placeId;
    private Double latitude;
    private Double longitude;

    private String profession;
    private String organization;

    // Bank/payment info — copied from KycDocument on KYC approval
    private String upiId;
    private String bankName;
    private String maskedBankAccount;
    private String bankIfscCode;

    @ElementCollection
    @CollectionTable(
            name = "user_categories",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "category")
    private Set<String> interestedCategories = new HashSet<>();

    private Integer totalProjectsBacked = 0;
    private Double totalAmountBacked = 0.0;
    private Integer totalProjectsCreated = 0;
    private Double totalFundsRaised = 0.0;


    public void addRole(Role role) {
        this.roles.add(role);
    }

    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }
}