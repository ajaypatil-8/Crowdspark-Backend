package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CreateDonationRequest;
import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.RewardTier;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.DonationService;
import Crowdspark.Crowdspark.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import Crowdspark.Crowdspark.service.EmailService;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DonationServiceImpl implements DonationService {

    private final DonationRepository    donationRepository;
    private final ProjectRepository     projectRepository;
    private final UserRepository        userRepository;
    private final RewardTierRepository  rewardTierRepository;
    private final NotificationService   notificationService;
    private final EmailService emailService;

    @Override
    @Transactional
    @CacheEvict(value = {"projectDetails", "exploreFeed"}, allEntries = true)
    public DonationResponse donate(CreateDonationRequest request, Long backerId) {

        // 1. Load backer
        User backer = userRepository.findById(backerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 2. Load & validate project
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        // ── GUARD: creator cannot back their own campaign ─────────────────────
        if (project.getCreator().getId().equals(backerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You cannot back your own campaign");
        }

        // ── GUARD: project must be APPROVED ───────────────────────────────────
        if (project.getStatus() != ProjectStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Project is not accepting donations");
        }

        // ── GUARD: deadline not passed ────────────────────────────────────────
        if (project.getDeadline().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Project funding deadline has passed");
        }

        // ── GUARD: cap donation to remaining amount ───────────────────────────
        double remaining = project.getGoalAmount() - project.getCurrentAmount();
        if (remaining <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This project has already reached its funding goal");
        }
        if (request.getAmount() > remaining) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("Amount exceeds remaining goal. Maximum you can contribute is ₹%.0f", remaining));
        }

        // 3. Optional reward tier validation
        RewardTier rewardTier = null;
        if (request.getRewardTierId() != null) {
            rewardTier = rewardTierRepository.findById(request.getRewardTierId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reward tier not found"));
            if (!rewardTier.getProject().getId().equals(project.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Reward tier does not belong to this project");
            }
            if (request.getAmount() < rewardTier.getMinimumAmount()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Amount must be at least ₹" + rewardTier.getMinimumAmount() + " for this reward tier");
            }
        }

        // 4. Build and save donation
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

        // 5. Update project.currentAmount
        double newTotal = project.getCurrentAmount() + request.getAmount();
        project.setCurrentAmount(newTotal);

        // ── AUTO-CLOSE: goal reached → close campaign ─────────────────────────
        if (newTotal >= project.getGoalAmount()) {
            project.setStatus(ProjectStatus.CLOSED);
            notificationService.notifyCreatorGoalReached(project);
        }

        projectRepository.save(project);

        // 6. Update backer stats
        backer.setTotalProjectsBacked(backer.getTotalProjectsBacked() + 1);
        backer.setTotalAmountBacked(backer.getTotalAmountBacked() + request.getAmount());
        userRepository.save(backer);

        // 7. Update creator funds raised
        User creator = project.getCreator();
        creator.setTotalFundsRaised(creator.getTotalFundsRaised() + request.getAmount());
        userRepository.save(creator);

        // 8. Notify creator of new backer
        notificationService.notifyCreatorBacked(project, backer, request.getAmount());

        emailService.sendBackerReceiptEmail(
                backer.getEmail(),
                backer.getName(),
                project.getTitle(),
                project.getId(),
                saved.getId(),
                saved.getAmount(),
                saved.getTransactionId(),
                rewardTier != null ? rewardTier.getTitle() : null,
                saved.getPaidAt()
        );

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
