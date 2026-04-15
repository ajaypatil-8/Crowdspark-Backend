package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.BackerDashboardResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.BackerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BackerServiceImpl implements BackerService {

    private final DonationRepository donationRepository;
    private final UserRepository     userRepository;

    @Override
    public BackerDashboardResponse getDashboard(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Donation> donations =
                donationRepository.findByBacker_IdOrderByCreatedAtDesc(userId);

        List<BackerDashboardResponse.BackedProjectDto> list = donations.stream()
                .map(d -> {

                    // thumbnail from project media
                    String thumbnail = d.getProject().getMedia().stream()
                            .filter(m -> m.getUsage() == MediaUsage.THUMBNAIL)
                            .map(ProjectMedia::getMediaUrl)
                            .findFirst()
                            .orElse(null);

                    return BackerDashboardResponse.BackedProjectDto.builder()
                            .donationId(d.getId())
                            .projectId(d.getProject().getId())
                            .projectTitle(d.getProject().getTitle())
                            .projectThumbnailUrl(thumbnail)
                            .projectStatus(d.getProject().getStatus().name())
                            .amountBacked(d.getAmount())
                            .paymentStatus(d.getPaymentStatus().name())
                            .rewardTierId(d.getRewardTier() != null ? d.getRewardTier().getId() : null)
                            .rewardTierTitle(d.getRewardTier() != null ? d.getRewardTier().getTitle() : null)
                            .creatorUsername(d.getProject().getCreator().getUsername())
                            .backedAt(d.getCreatedAt())
                            .projectDeadline(d.getProject().getDeadline())
                            .build();
                })
                .toList();

        // Use live DB totals (source of truth) rather than cached user fields
        Double totalAmount = donationRepository.sumSuccessfulByBacker(userId);

        return BackerDashboardResponse.builder()
                .totalProjectsBacked((long) list.size())
                .totalAmountBacked(totalAmount != null ? totalAmount : 0.0)
                .backedProjects(list)
                .build();
    }
}
