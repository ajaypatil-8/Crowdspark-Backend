// src/main/java/Crowdspark/Crowdspark/service/RewardClaimService.java

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.RewardClaimResponse;
import Crowdspark.Crowdspark.dto.RewardClaimShippingRequest;
import Crowdspark.Crowdspark.dto.RewardClaimStatusRequest;
import Crowdspark.Crowdspark.entity.Donation;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RewardClaimService {

    /**
     * Auto-called by PaymentService after a donation is confirmed SUCCESS.
     * Creates a RewardClaim only when the donation has a rewardTier set.
     * Idempotent — safe to call twice.
     */
    void createClaimForDonation(Donation donation);

    /**
     * GET /api/projects/{projectId}/reward-claims
     * Creator sees all claims for their project, newest first.
     * Optionally filtered by status string (null = all).
     */
    Page<RewardClaimResponse> getProjectClaims(Long projectId, Long creatorId,
                                                String statusFilter, int page, int size);

    /**
     * GET /api/backer/reward-claims
     * Backer sees all their own claims across all projects.
     */
    List<RewardClaimResponse> getMyBackerClaims(Long backerId);

    /**
     * PUT /api/reward-claims/{claimId}/status
     * Creator advances the status (PROCESSING → SHIPPED → FULFILLED, or CANCELLED).
     * Fires notification to backer on each transition.
     */
    RewardClaimResponse updateStatus(Long claimId, RewardClaimStatusRequest request, Long creatorId);

    /**
     * PUT /api/reward-claims/{claimId}/shipping
     * Backer submits or updates their shipping address.
     * Only allowed while status is PENDING or PROCESSING.
     */
    RewardClaimResponse updateShipping(Long claimId, RewardClaimShippingRequest request, Long backerId);

    /**
     * Auto-called by RefundTransactionExecutor after a donation's refund
     * completes. Cancels the associated RewardClaim (if one exists) and
     * gives the reserved unit back to the tier's available count — the
     * backer got their money back, so the reward is no longer owed. No-op
     * if there's no claim for this donation, or if it's already
     * CANCELLED/FULFILLED (a refund shouldn't retroactively "un-deliver" a
     * reward that already shipped).
     */
    void cancelClaimForRefundedDonation(Donation donation);

    /**
     * GET /api/projects/{projectId}/reward-claims/summary
     * Returns count per status — used in creator dashboard card.
     */
    java.util.Map<String, Long> getProjectClaimSummary(Long projectId, Long creatorId);
}
