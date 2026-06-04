// src/main/java/Crowdspark/Crowdspark/dto/DataExportResponse.java
package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GDPR data export — returned by GET /auth/me/data-export
 * Contains all personal data we hold about the user.
 */
@Data
@Builder
public class DataExportResponse {

    private String exportedAt;

    // ── Profile ───────────────────────────────────────────────────────────────
    private ProfileData profile;

    // ── Activity ──────────────────────────────────────────────────────────────
    private List<DonationData>       donations;
    private List<ProjectSummaryData> createdProjects;
    private List<ProjectSummaryData> savedProjects;
    private List<CommentData>        comments;

    // ── Nested types ──────────────────────────────────────────────────────────

    @Data @Builder
    public static class ProfileData {
        private Long   id;
        private String name;
        private String username;
        private String email;
        private String phoneNumber;
        private String bio;
        private String about;
        private String gender;
        private String dateOfBirth;
        private String city;
        private String state;
        private String country;
        private String profession;
        private String organization;
        private String websiteUrl;
        private String linkedinUrl;
        private String instagramUrl;
        private String twitterUrl;
        private String accountStatus;
        private String createdAt;
        private String lastLoginAt;
        private Integer totalProjectsBacked;
        private Double  totalAmountBacked;
        private Integer totalProjectsCreated;
        private Double  totalFundsRaised;
    }

    @Data @Builder
    public static class DonationData {
        private Long   id;
        private Long   projectId;
        private String projectTitle;
        private Double amount;
        private String paymentStatus;
        private String createdAt;
        private String paidAt;
    }

    @Data @Builder
    public static class ProjectSummaryData {
        private Long   id;
        private String title;
        private String status;
        private Double goalAmount;
        private Double currentAmount;
        private String createdAt;
        private String deadline;
    }

    @Data @Builder
    public static class CommentData {
        private Long   id;
        private Long   projectId;
        private String projectTitle;
        private String content;
        private String createdAt;
    }
}
