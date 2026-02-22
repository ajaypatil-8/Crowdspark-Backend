package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.Gender;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class UpdateProfileRequest {

    // ─────────────────────────────────────────
    // COMMON — Backer + Creator
    // ─────────────────────────────────────────
    @Size(max = 160, message = "Bio must be under 160 characters")
    private String bio;

    @Size(max = 1000, message = "About must be under 1000 characters")
    private String about;

    private Gender gender;
    private LocalDate dateOfBirth;

    private String websiteUrl;
    private String linkedinUrl;
    private String instagramUrl;
    private String twitterUrl;

    // ─────────────────────────────────────────
    // LOCATION (from Google Places API on frontend)
    // ─────────────────────────────────────────
    private String addressLine;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private String placeId;       // Google Places place_id
    private Double latitude;
    private Double longitude;

    // ─────────────────────────────────────────
    // CREATOR PROFESSIONAL INFO
    // ─────────────────────────────────────────
    private String profession;
    private String organization;
    private Set<String> interestedCategories;
}