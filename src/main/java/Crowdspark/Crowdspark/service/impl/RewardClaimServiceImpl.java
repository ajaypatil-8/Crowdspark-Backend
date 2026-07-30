// src/main/java/Crowdspark/Crowdspark/service/impl/RewardClaimServiceImpl.java

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.RewardClaimResponse;
import Crowdspark.Crowdspark.dto.RewardClaimShippingRequest;
import Crowdspark.Crowdspark.dto.RewardClaimStatusRequest;
import Crowdspark.Crowdspark.entity.*;
import Crowdspark.Crowdspark.entity.type.NotificationType;
import Crowdspark.Crowdspark.entity.type.RewardClaimStatus;
import Crowdspark.Crowdspark.repository.*;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.RewardClaimService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardClaimServiceImpl implements RewardClaimService {

    private final RewardClaimRepository claimRepository;
    private final ProjectRepository     projectRepository;
    private final UserRepository        userRepository;
    private final NotificationService   notificationService;
    // BUG FIX (Feature #24): atomic quantity tracking for limited-quantity tiers.
    private final RewardTierRepository  rewardTierRepository;

    // ── Create on payment success ─────────────────────────────────────────

    @Override
    @Transactional
    public void createClaimForDonation(Donation donation) {
        if (donation.getRewardTier() == null) return;          // no reward chosen
        if (claimRepository.existsByDonation_Id(donation.getId())) return; // idempotent

        RewardClaim claim = new RewardClaim();
        claim.setDonation(donation);
        claim.setBacker(donation.getBacker());
        claim.setRewardTier(donation.getRewardTier());
        claim.setProject(donation.getProject());
        claim.setStatus(RewardClaimStatus.PENDING);
        claimRepository.save(claim);

        // BUG FIX (Feature #24): atomically reserve a unit for limited-
        // quantity tiers. This is the second line of defense (after
        // PaymentServiceImpl's sold-out check at order creation) for the
        // rare race where two backers pay for the last unit at nearly the
        // same instant -- the conditional UPDATE means at most one of them
        // can actually decrement past zero. Payment has already succeeded
        // by this point, so this never blocks claim creation itself: the
        // backer paid, so their claim is recorded either way; this only
        // keeps the remaining-count number itself honest.
        RewardTier tier = donation.getRewardTier();
        if (tier.getLimitedQuantity() != null) {
            rewardTierRepository.decrementQuantityIfAvailable(tier.getId());
        }

        log.info("Reward claim created: donationId={} tierId={} backerId={}",
                donation.getId(), donation.getRewardTier().getId(), donation.getBacker().getId());
    }

    // ── Cancel on refund ───────────────────────────────────────────────────
    // BUG FIX (Feature #24): nothing anywhere in the codebase ever called
    // this before -- when a campaign failed and RefundTransactionExecutor
    // processed refunds, any associated RewardClaim was left exactly as it
    // was (PENDING/PROCESSING/etc.) forever, contradicting CANCELLED's own
    // documented meaning ("donation refunded; reward no longer owed"). A
    // creator's fulfillment view would keep showing a claim as open for a
    // backer who had already gotten their money back -- and a limited
    // tier's unit stayed marked as consumed even though it was never
    // actually delivered.

    @Override
    @Transactional
    public void cancelClaimForRefundedDonation(Donation donation) {
        claimRepository.findByDonation_Id(donation.getId()).ifPresent(claim -> {
            if (claim.getStatus() == RewardClaimStatus.CANCELLED
                    || claim.getStatus() == RewardClaimStatus.FULFILLED) {
                return; // already terminal, or already delivered -- a refund doesn't undo that
            }
            claim.setStatus(RewardClaimStatus.CANCELLED);
            claimRepository.save(claim);

            RewardTier tier = claim.getRewardTier();
            if (tier.getLimitedQuantity() != null) {
                rewardTierRepository.incrementQuantity(tier.getId());
            }

            log.info("Reward claim {} auto-cancelled: donation {} was refunded",
                    claim.getId(), donation.getId());
            notifyBackerClaimUpdate(claim);
        });
    }

    // ── Creator: list project claims ──────────────────────────────────────

    @Override
    public Page<RewardClaimResponse> getProjectClaims(Long projectId, Long creatorId,
                                                       String statusFilter, int page, int size) {
        validateCreatorOwnership(projectId, creatorId);

        PageRequest pr = PageRequest.of(page, size);
        Page<RewardClaim> claims;

        if (statusFilter != null && !statusFilter.isBlank()) {
            RewardClaimStatus status;
            try { status = RewardClaimStatus.valueOf(statusFilter.toUpperCase()); }
            catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid status filter: " + statusFilter);
            }
            claims = claimRepository.findByProject_IdAndStatusOrderByClaimedAtDesc(
                    projectId, status, pr);
        } else {
            claims = claimRepository.findByProject_IdOrderByClaimedAtDesc(projectId, pr);
        }
        return claims.map(this::toResponse);
    }

    // ── Backer: list own claims ───────────────────────────────────────────

    @Override
    public List<RewardClaimResponse> getMyBackerClaims(Long backerId) {
        return claimRepository.findByBacker_IdOrderByClaimedAtDesc(backerId)
                .stream().map(this::toResponse).toList();
    }

    // ── Creator: update status ────────────────────────────────────────────

    @Override
    @Transactional
    public RewardClaimResponse updateStatus(Long claimId,
                                             RewardClaimStatusRequest request,
                                             Long creatorId) {
        RewardClaim claim = loadClaim(claimId);
        validateCreatorOwnership(claim.getProject().getId(), creatorId);

        RewardClaimStatus newStatus;
        try { newStatus = RewardClaimStatus.valueOf(request.getStatus()); }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }

        // Guard: can't un-cancel or go backwards
        if (claim.getStatus() == RewardClaimStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cancelled claims cannot be updated");
        }
        if (claim.getStatus() == RewardClaimStatus.FULFILLED &&
                newStatus != RewardClaimStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Fulfilled claims can only be cancelled");
        }

        // SHIPPED requires tracking number
        if (newStatus == RewardClaimStatus.SHIPPED &&
                (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tracking number is required when marking a claim as SHIPPED");
        }

        claim.setStatus(newStatus);
        if (request.getTrackingNumber() != null) {
            claim.setTrackingNumber(request.getTrackingNumber().trim());
        }
        if (request.getFulfillmentNote() != null) {
            claim.setFulfillmentNote(request.getFulfillmentNote().trim());
        }
        if (newStatus == RewardClaimStatus.FULFILLED ||
                newStatus == RewardClaimStatus.SHIPPED) {
            claim.setFulfilledAt(LocalDateTime.now());
        }

        RewardClaim saved = claimRepository.save(claim);
        log.info("Reward claim {} updated to {} by creator={}", claimId, newStatus, creatorId);

        // Notify backer
        notifyBackerClaimUpdate(saved);

        return toResponse(saved);
    }

    // ── Backer: update shipping ───────────────────────────────────────────

    @Override
    @Transactional
    public RewardClaimResponse updateShipping(Long claimId,
                                               RewardClaimShippingRequest request,
                                               Long backerId) {
        RewardClaim claim = loadClaim(claimId);

        if (!claim.getBacker().getId().equals(backerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only update your own claims");
        }
        if (claim.getStatus() == RewardClaimStatus.SHIPPED ||
                claim.getStatus() == RewardClaimStatus.FULFILLED ||
                claim.getStatus() == RewardClaimStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot update shipping details — reward is already " +
                    claim.getStatus().name().toLowerCase());
        }

        claim.setShippingName(request.getShippingName().trim());
        claim.setShippingAddress(request.getShippingAddress().trim());
        claim.setShippingCity(request.getShippingCity().trim());
        claim.setShippingPincode(request.getShippingPincode().trim());
        claim.setShippingCountry(request.getShippingCountry() != null
                ? request.getShippingCountry().trim() : "India");
        claim.setShippingPhone(request.getShippingPhone() != null
                ? request.getShippingPhone().trim() : null);

        RewardClaim saved = claimRepository.save(claim);
        log.info("Shipping updated for claim={} by backer={}", claimId, backerId);
        return toResponse(saved);
    }

    // ── Summary ───────────────────────────────────────────────────────────

    @Override
    public Map<String, Long> getProjectClaimSummary(Long projectId, Long creatorId) {
        validateCreatorOwnership(projectId, creatorId);

        Map<String, Long> summary = new HashMap<>();
        for (RewardClaimStatus s : RewardClaimStatus.values()) summary.put(s.name(), 0L);

        claimRepository.countByStatusForProject(projectId).forEach(row -> {
            String status = ((RewardClaimStatus) row[0]).name();
            Long   count  = ((Number) row[1]).longValue();
            summary.put(status, count);
        });
        return summary;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void validateCreatorOwnership(Long projectId, Long creatorId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found"));
        if (!project.getCreator().getId().equals(creatorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the project creator can access reward claims");
        }
    }

    private RewardClaim loadClaim(Long claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reward claim not found"));
    }

    private void notifyBackerClaimUpdate(RewardClaim claim) {
        String title;
        String msg;
        switch (claim.getStatus()) {
            case PROCESSING -> {
                title = "🎁 Your reward is being prepared";
                msg   = "The creator of \"" + claim.getProject().getTitle() +
                        "\" is processing your reward: " + claim.getRewardTier().getTitle();
            }
            case SHIPPED -> {
                title = "📦 Your reward has been shipped!";
                msg   = "Your reward \"" + claim.getRewardTier().getTitle() +
                        "\" is on the way." +
                        (claim.getTrackingNumber() != null
                            ? " Tracking: " + claim.getTrackingNumber() : "");
            }
            case FULFILLED -> {
                title = "✅ Your reward has been delivered!";
                msg   = "Your reward \"" + claim.getRewardTier().getTitle() +
                        "\" from \"" + claim.getProject().getTitle() + "\" is complete.";
            }
            case CANCELLED -> {
                title = "❌ Reward claim cancelled";
                msg   = "Your reward claim for \"" + claim.getRewardTier().getTitle() +
                        "\" has been cancelled.";
            }
            default -> { return; }
        }
        notificationService.sendGeneralNotification(
                claim.getBacker(), title, msg,
                "/dashboard/backed", claim.getId());
    }

    private RewardClaimResponse toResponse(RewardClaim c) {
        User backer = c.getBacker();
        return RewardClaimResponse.builder()
                .id(c.getId())
                .donationId(c.getDonation().getId())
                .backerId(backer.getId())
                .backerName(backer.getName())
                .backerUsername(backer.getUsername())
                .backerProfileImageUrl(backer.getProfileImageUrl())
                .projectId(c.getProject().getId())
                .projectTitle(c.getProject().getTitle())
                .rewardTierId(c.getRewardTier().getId())
                .rewardTierTitle(c.getRewardTier().getTitle())
                .rewardTierMinAmount(c.getRewardTier().getMinimumAmount())
                .donationAmount(c.getDonation().getAmount())
                .status(c.getStatus().name())
                .shippingName(c.getShippingName())
                .shippingAddress(c.getShippingAddress())
                .shippingCity(c.getShippingCity())
                .shippingPincode(c.getShippingPincode())
                .shippingCountry(c.getShippingCountry())
                .shippingPhone(c.getShippingPhone())
                .shippingProvided(c.getShippingAddress() != null)
                .trackingNumber(c.getTrackingNumber())
                .fulfillmentNote(c.getFulfillmentNote())
                .claimedAt(c.getClaimedAt())
                .fulfilledAt(c.getFulfilledAt())
                .build();
    }
}
