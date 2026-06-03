// src/main/java/Crowdspark/Crowdspark/dto/UpdateProfileRequest.java
// CHANGES: Added @Size on URL fields, profession, organization, pincode.
//          Added @Pattern on URLs to prevent javascript: injection.

package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.Gender;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class UpdateProfileRequest {

    // ── Common ────────────────────────────────────────────────────────────────

    @Size(max = 160, message = "Bio must be under 160 characters")
    private String bio;

    @Size(max = 1000, message = "About must be under 1000 characters")
    private String about;

    private Gender  gender;
    private LocalDate dateOfBirth;

    // ── Social URLs — only http/https allowed, no javascript: ────────────────

    @Size(max = 255)
    @Pattern(regexp = "^$|^https?://.*",
             message = "Website URL must start with http:// or https://")
    private String websiteUrl;

    @Size(max = 255)
    @Pattern(regexp = "^$|^https://www\\.linkedin\\.com/.*",
             message = "Must be a valid LinkedIn URL")
    private String linkedinUrl;

    @Size(max = 255)
    @Pattern(regexp = "^$|^https://www\\.instagram\\.com/.*",
             message = "Must be a valid Instagram URL")
    private String instagramUrl;

    @Size(max = 255)
    @Pattern(regexp = "^$|^https://(www\\.)?(twitter|x)\\.com/.*",
             message = "Must be a valid Twitter/X URL")
    private String twitterUrl;

    // ── Location ──────────────────────────────────────────────────────────────

    @Size(max = 255)
    private String addressLine;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 100)
    private String country;

    @Size(max = 10)
    @Pattern(regexp = "^$|^\\d{6}$", message = "Pincode must be 6 digits")
    private String pincode;

    @Size(max = 255)
    private String placeId;

    private Double latitude;
    private Double longitude;

    // ── Creator Professional Info ─────────────────────────────────────────────

    @Size(max = 100, message = "Profession must be under 100 characters")
    private String profession;

    @Size(max = 150, message = "Organization must be under 150 characters")
    private String organization;

    @Size(max = 10, message = "Maximum 10 interested categories")
    private Set<String> interestedCategories;
}
