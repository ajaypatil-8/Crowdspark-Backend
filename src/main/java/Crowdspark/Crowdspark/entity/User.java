package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.entity.type.Gender;
import Crowdspark.Crowdspark.entity.type.AccountStatus;
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
@Table(name = "users")
public class User {

    // ─────────────────────────────────────────
    // PRIMARY KEY
    // ─────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─────────────────────────────────────────
    // BASIC AUTH FIELDS (Registration)
    // ─────────────────────────────────────────
    private String name;

    @Column(unique = true)
    private String username;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phoneNumber;

    private String password;

    private String provider;       // LOCAL, GOOGLE
    private String providerId;

    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean isEnabled = true;
    private boolean isLocked = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;


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



    // PAN Card
    private String panNumber;
    private String panCardImageUrl;
    private String panCardImagePublicId;

    // Aadhaar
    private String aadhaarNumber;
    private String aadhaarFrontImageUrl;
    private String aadhaarFrontPublicId;
    private String aadhaarBackImageUrl;
    private String aadhaarBackPublicId;


    private boolean isKycVerified = false;
    private LocalDateTime kycSubmittedAt;
    private LocalDateTime kycVerifiedAt;


    private String bankAccountHolderName;
    private String bankAccountNumber;
    private String bankIfscCode;
    private String bankName;
    private String bankBranchName;


    private String upiId;


    private String profession;
    private String organization;

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