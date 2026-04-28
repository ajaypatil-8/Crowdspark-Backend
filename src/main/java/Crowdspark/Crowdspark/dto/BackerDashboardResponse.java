package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BackerDashboardResponse {

    // ── stats block ────────────────────────────────────────────────────────────
    private Long   totalProjectsBacked;
    private Double totalAmountBacked;

    // ── backed project list ────────────────────────────────────────────────────
    private List<BackedProjectDto> backedProjects;

    @Data
    @Builder
    public static class BackedProjectDto {

        private Long   donationId;
        private Long   projectId;
        private String projectTitle;

        // BUG FIX: was "projectThumbnailUrl" — frontend expects "thumbnailUrl"
        private String thumbnailUrl;

        // BUG FIX: was "projectStatus" — frontend expects "status"
        private String status;

        // NEW: added for progress bar in backed page
        private Double goalAmount;
        private Double currentAmount;
        private Double fundedPercentage;

        private Double amountBacked;
        private String paymentStatus;

        private Long   rewardTierId;
        private String rewardTierTitle;

        private String creatorUsername;

        private LocalDateTime backedAt;
        private LocalDateTime projectDeadline;
    }
}
