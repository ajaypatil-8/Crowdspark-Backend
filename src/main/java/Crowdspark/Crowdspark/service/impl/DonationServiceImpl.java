package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.service.DonationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// AUDIT FIX (Feature #1): this class used to also implement donate(), which
// created a SUCCESS donation directly from a client-supplied transactionId
// with no Razorpay verification at all -- a full payment bypass reachable by
// any authenticated user. That method, its DonationController endpoint, and
// its DonationService interface entry have all been removed.
//
// Donations are now only ever created/confirmed by PaymentServiceImpl, via:
//   - createOrder()      -> PENDING donation + real Razorpay order
//   - verifyAndConfirm()  -> marks SUCCESS only after a verified HMAC signature
//   - confirmFromWebhook() -> marks SUCCESS from Razorpay's own server callback
// This class is now read-only history lookups, so it no longer needs
// ProjectRepository, UserRepository, RewardTierRepository, NotificationService,
// EmailService, or FundingStreamService -- all of that lived only in donate().
@Service
@RequiredArgsConstructor
public class DonationServiceImpl implements DonationService {

    private final DonationRepository donationRepository;

    @Override
    public List<DonationResponse> getMyDonations(Long backerId) {
        return donationRepository.findByBacker_IdOrderByCreatedAtDesc(backerId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<DonationResponse> getProjectDonations(Long projectId) {
        return donationRepository.findByProject_IdOrderByCreatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    private DonationResponse toResponse(Donation d) {
        String thumbnail = d.getProject().getMedia().stream()
                .filter(m -> m.getUsage() == MediaUsage.THUMBNAIL)
                .map(m -> m.getMediaUrl())
                .findFirst().orElse(null);

        return DonationResponse.builder()
                .id(d.getId())
                .projectId(d.getProject().getId())
                .projectTitle(d.getProject().getTitle())
                .projectThumbnailUrl(thumbnail)
                .backerId(d.getBacker().getId())
                .backerUsername(d.getBacker().getUsername())
                .amount(d.getAmount())
                .paymentStatus(d.getPaymentStatus().name())
                .transactionId(d.getTransactionId())
                .message(d.getMessage())
                .rewardTierId(d.getRewardTier() != null ? d.getRewardTier().getId() : null)
                .rewardTierTitle(d.getRewardTier() != null ? d.getRewardTier().getTitle() : null)
                .createdAt(d.getCreatedAt())
                .paidAt(d.getPaidAt())
                .build();
    }
}
