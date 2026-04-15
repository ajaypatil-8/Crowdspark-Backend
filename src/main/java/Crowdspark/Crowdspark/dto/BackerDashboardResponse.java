package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BackerDashboardResponse {

    // ── stats block ────────────────────────────────────────────────────────────
    private Long totalProjectsBacked;
    private Double totalAmountBacked;

    // ── backed project list ────────────────────────────────────────────────────
    private List<BackedProjectDto> backedProjects;

    @Data
    @Builder
    public static class BackedProjectDto {

        private Long donationId;
        private Long projectId;
        private String projectTitle;
        private String projectThumbnailUrl;
        private String projectStatus;       // APPROVED / FUNDED / EXPIRED etc.

        private Double amountBacked;
        private String paymentStatus;       // PENDING / SUCCESS / FAILED

        // reward tier backer chose (nullable)
        private Long rewardTierId;
        private String rewardTierTitle;

        private String creatorUsername;

        private LocalDateTime backedAt;
        private LocalDateTime projectDeadline;
    }
}
