// src/test/java/Crowdspark/Crowdspark/service/DonationServiceImplTest.java
// Feature #13: DonationService is explicitly named in the feature spec
// ("JUnit 5 + Mockito tests for: AuthService, ProjectService, DonationService,
// PaymentService") but had no test file at all before this one.
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CreateDonationRequest;
import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.RewardTier;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.impl.DonationServiceImpl;
import Crowdspark.Crowdspark.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DonationServiceImpl Tests")
class DonationServiceImplTest {

    @Mock DonationRepository   donationRepository;
    @Mock ProjectRepository    projectRepository;
    @Mock UserRepository       userRepository;
    @Mock RewardTierRepository rewardTierRepository;
    @Mock NotificationService  notificationService;
    @Mock EmailService         emailService;

    @InjectMocks DonationServiceImpl donationService;

    private User    backer;
    private User    creator;
    private Project project;

    @BeforeEach
    void setUp() {
        backer  = TestDataFactory.backerUser();
        creator = TestDataFactory.creatorUser();
        project = TestDataFactory.approvedProject(creator);
        // goalAmount=100_000, currentAmount=25_000 → remaining=75_000
    }

    private CreateDonationRequest requestFor(double amount) {
        CreateDonationRequest req = new CreateDonationRequest();
        req.setProjectId(project.getId());
        req.setAmount(amount);
        req.setTransactionId("pay_test123");
        req.setMessage("Good luck!");
        return req;
    }

    private void stubDonationSave() {
        given(donationRepository.save(any())).willAnswer(inv -> {
            Donation d = inv.getArgument(0);
            d.setId(500L);
            d.setCreatedAt(LocalDateTime.now());
            return d;
        });
    }

    // ─── donate: happy paths ──────────────────────────────────────────────────

    @Test
    @DisplayName("donate succeeds and returns a mapped response")
    void donate_succeeds_withValidRequest() {
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        stubDonationSave();

        DonationResponse response = donationService.donate(requestFor(5_000.0), backer.getId());

        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getProjectId()).isEqualTo(project.getId());
        assertThat(response.getAmount()).isEqualTo(5_000.0);
        assertThat(response.getPaymentStatus()).isEqualTo("SUCCESS");
        assertThat(project.getCurrentAmount()).isEqualTo(30_000.0); // 25k + 5k

        verify(projectRepository).save(project);
        verify(userRepository).save(backer);
        verify(userRepository).save(creator);
        verify(notificationService).notifyCreatorBacked(project, backer, 5_000.0);
        verify(emailService).sendBackerReceiptEmail(
                eq(backer.getEmail()), eq(backer.getName()), eq(project.getTitle()),
                eq(project.getId()), eq(500L), eq(5_000.0), eq("pay_test123"), isNull(), any());
    }

    @Test
    @DisplayName("donate updates backer and creator running totals")
    void donate_updatesBackerAndCreatorStats() {
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        stubDonationSave();

        donationService.donate(requestFor(5_000.0), backer.getId());

        assertThat(backer.getTotalProjectsBacked()).isEqualTo(1);
        assertThat(backer.getTotalAmountBacked()).isEqualTo(5_000.0);
        assertThat(creator.getTotalFundsRaised()).isEqualTo(5_000.0);
    }

    @Test
    @DisplayName("donate accepts a valid reward tier and includes it in the response")
    void donate_succeeds_withValidRewardTier() {
        RewardTier tier = new RewardTier();
        tier.setId(50L);
        tier.setProject(project);
        tier.setTitle("Bronze Backer");
        tier.setMinimumAmount(1_000.0);

        CreateDonationRequest req = requestFor(5_000.0);
        req.setRewardTierId(50L);

        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        given(rewardTierRepository.findById(50L)).willReturn(Optional.of(tier));
        stubDonationSave();

        DonationResponse response = donationService.donate(req, backer.getId());

        assertThat(response.getRewardTierId()).isEqualTo(50L);
        assertThat(response.getRewardTierTitle()).isEqualTo("Bronze Backer");
    }

    @Test
    @DisplayName("donate auto-closes the project once the goal is exactly reached")
    void donate_autoCloses_whenGoalReached() {
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        stubDonationSave();

        // remaining = 100_000 - 25_000 = 75_000 exactly
        donationService.donate(requestFor(75_000.0), backer.getId());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CLOSED);
        verify(notificationService).notifyCreatorGoalReached(project);
    }

    @Test
    @DisplayName("donate leaves the project open when the goal isn't reached yet")
    void donate_doesNotAutoClose_whenGoalNotYetReached() {
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        stubDonationSave();

        donationService.donate(requestFor(10_000.0), backer.getId());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.APPROVED);
        verify(notificationService, never()).notifyCreatorGoalReached(any());
    }

    // ─── donate: guard clauses ─────────────────────────────────────────────────

    @Test
    @DisplayName("donate throws 404 when the backer doesn't exist")
    void donate_throws404_whenBackerNotFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> donationService.donate(requestFor(1_000.0), 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("donate throws 404 when the project doesn't exist")
    void donate_throws404_whenProjectNotFound() {
        CreateDonationRequest req = requestFor(1_000.0);
        req.setProjectId(999L);
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> donationService.donate(req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    @DisplayName("donate throws 403 when the creator tries to back their own campaign")
    void donate_throws403_whenCreatorBacksOwnProject() {
        given(userRepository.findById(creator.getId())).willReturn(Optional.of(creator));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        assertThatThrownBy(() -> donationService.donate(requestFor(1_000.0), creator.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot back your own campaign");
    }

    @Test
    @DisplayName("donate throws 400 when the project isn't APPROVED")
    void donate_throws400_whenProjectNotApproved() {
        project.setStatus(ProjectStatus.PENDING);
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        assertThatThrownBy(() -> donationService.donate(requestFor(1_000.0), backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not accepting donations");
    }

    @Test
    @DisplayName("donate throws 400 when the funding deadline has passed")
    void donate_throws400_whenDeadlinePassed() {
        project.setDeadline(LocalDateTime.now().minusHours(1));
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        assertThatThrownBy(() -> donationService.donate(requestFor(1_000.0), backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("deadline has passed");
    }

    @Test
    @DisplayName("donate throws 400 when the project has already reached its goal")
    void donate_throws400_whenGoalAlreadyReached() {
        project.setCurrentAmount(project.getGoalAmount());
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        assertThatThrownBy(() -> donationService.donate(requestFor(1_000.0), backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already reached its funding goal");
    }

    @Test
    @DisplayName("donate throws 400 when the amount exceeds the remaining goal")
    void donate_throws400_whenAmountExceedsRemaining() {
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        // remaining = 75_000; asking for more than that
        assertThatThrownBy(() -> donationService.donate(requestFor(80_000.0), backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Maximum you can contribute is");
    }

    @Test
    @DisplayName("donate throws 404 when the chosen reward tier doesn't exist")
    void donate_throws404_whenRewardTierNotFound() {
        CreateDonationRequest req = requestFor(5_000.0);
        req.setRewardTierId(999L);
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        given(rewardTierRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> donationService.donate(req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Reward tier not found");
    }

    @Test
    @DisplayName("donate throws 400 when the reward tier belongs to a different project")
    void donate_throws400_whenRewardTierBelongsToDifferentProject() {
        Project otherProject = TestDataFactory.approvedProject(creator);
        otherProject.setId(999L);
        RewardTier tier = new RewardTier();
        tier.setId(50L);
        tier.setProject(otherProject);
        tier.setMinimumAmount(1_000.0);

        CreateDonationRequest req = requestFor(5_000.0);
        req.setRewardTierId(50L);
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        given(rewardTierRepository.findById(50L)).willReturn(Optional.of(tier));

        assertThatThrownBy(() -> donationService.donate(req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to this project");
    }

    @Test
    @DisplayName("donate throws 400 when the amount is below the reward tier's minimum")
    void donate_throws400_whenAmountBelowRewardTierMinimum() {
        RewardTier tier = new RewardTier();
        tier.setId(50L);
        tier.setProject(project);
        tier.setMinimumAmount(10_000.0);

        CreateDonationRequest req = requestFor(2_000.0);
        req.setRewardTierId(50L);
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        given(rewardTierRepository.findById(50L)).willReturn(Optional.of(tier));

        assertThatThrownBy(() -> donationService.donate(req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Amount must be at least");
    }

    // ─── getMyDonations / getProjectDonations ─────────────────────────────────

    @Test
    @DisplayName("getMyDonations returns the backer's donation history, mapped")
    void getMyDonations_returnsMappedList() {
        Donation donation = TestDataFactory.successfulDonation(backer, project);
        given(donationRepository.findByBacker_IdOrderByCreatedAtDesc(backer.getId()))
                .willReturn(List.of(donation));

        List<DonationResponse> result = donationService.getMyDonations(backer.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProjectTitle()).isEqualTo("Test Campaign");
        assertThat(result.get(0).getBackerUsername()).isEqualTo("testbacker");
    }

    @Test
    @DisplayName("getMyDonations returns an empty list when the backer has no donations")
    void getMyDonations_returnsEmpty_whenNoDonations() {
        given(donationRepository.findByBacker_IdOrderByCreatedAtDesc(backer.getId()))
                .willReturn(List.of());

        assertThat(donationService.getMyDonations(backer.getId())).isEmpty();
    }

    @Test
    @DisplayName("getProjectDonations returns all donations for a project, mapped")
    void getProjectDonations_returnsMappedList() {
        Donation donation = TestDataFactory.successfulDonation(backer, project);
        given(donationRepository.findByProject_IdOrderByCreatedAtDesc(project.getId()))
                .willReturn(List.of(donation));

        List<DonationResponse> result = donationService.getProjectDonations(project.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProjectId()).isEqualTo(project.getId());
    }
}
