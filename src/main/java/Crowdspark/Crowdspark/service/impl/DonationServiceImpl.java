package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CreateDonationRequest;
import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.RewardTier;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.DonationService;
import Crowdspark.Crowdspark.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DonationServiceImpl implements DonationService {

    private final DonationRepository donationRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final RewardTierRepository rewardTierRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public DonationResponse donate(CreateDonationRequest request, Long backerId) {

        // 1. Load backer
        User backer = userRepository.findById(backerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 2. Load & validate project
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (project.getStatus() != ProjectStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project is not accepting donations");
        }

        if (project.getDeadline().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project funding deadline has passed");
        }

        // 3. Optional reward tier
        RewardTier rewardTier = null;
        if (request.getRewardTierId() != null) {
            rewardTier = rewardTierRepository.findById(request.getRewardTierId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reward tier not found"));
            if (!rewardTier.getProject().getId().equals(project.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reward tier does not belong to this project");
            }
            if (request.getAmount() < rewardTier.getMinimumAmount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Amount must be at least ₹" + rewardTier.getMinimumAmount() + " for this reward tier");
            }
        }

        // 4. Build donation record (SUCCESS immediately — real payment gateway would set this async)
        Donation donation = new Donation();
        donation.setBacker(backer);
        donation.setProject(project);
        donation.setAmount(request.getAmount());
        donation.setRewardTier(rewardTier);
        donation.setPaymentStatus(PaymentStatus.SUCCESS);
        donation.setTransactionId(request.getTransactionId());
        donation.setMessage(request.getMessage());
        donation.setPaidAt(LocalDateTime.now());

        Donation saved = donationRepository.save(donation);

        // 5. Increment project.currentAmount
        project.setCurrentAmount(project.getCurrentAmount() + request.getAmount());
        projectRepository.save(project);

        // 6. Increment user stats
        backer.setTotalProjectsBacked(backer.getTotalProjectsBacked() + 1);
        backer.setTotalAmountBacked(backer.getTotalAmountBacked() + request.getAmount());
        userRepository.save(backer);

        // 7. Creator funds raised
        User creator = project.getCreator();
        creator.setTotalFundsRaised(creator.getTotalFundsRaised() + request.getAmount());
        userRepository.save(creator);

        // 8. Notify creator
        notificationService.notifyCreatorBacked(project, backer, request.getAmount());

        return toResponse(saved);
    }

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

    // --- mapper ---

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
