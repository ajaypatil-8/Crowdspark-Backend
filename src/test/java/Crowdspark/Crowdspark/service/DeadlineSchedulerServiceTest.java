// src/test/java/Crowdspark/Crowdspark/service/DeadlineSchedulerServiceTest.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.service.impl.DeadlineSchedulerService;
import Crowdspark.Crowdspark.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeadlineSchedulerService Tests")
class DeadlineSchedulerServiceTest {

    @Mock ProjectRepository    projectRepository;
    @Mock DonationRepository   donationRepository;
    @Mock NotificationService  notificationService;
    @Mock RefundService        refundService;
    @Mock FundingStreamService fundingStreamService;
    @Mock EmailService         emailService;

    @InjectMocks DeadlineSchedulerService scheduler;

    private User    creator;
    private Project expiredFunded;
    private Project expiredFailed;

    @BeforeEach
    void setUp() {
        creator       = TestDataFactory.creatorUser();
        expiredFunded = TestDataFactory.fundedExpiredProject(creator);
        expiredFailed = TestDataFactory.expiredProject(creator);
        // expiredFailed: currentAmount=25k, goalAmount=100k → FAILED
    }

    @Test
    @DisplayName("processExpiredCampaigns: no expired projects → no action")
    void processExpiredCampaigns_noAction_whenNoneExpired() {
        given(projectRepository.findExpiredApprovedProjects(any()))
                .willReturn(List.of());

        scheduler.processExpiredCampaigns();

        verify(projectRepository, never()).save(any());
        verifyNoInteractions(notificationService, refundService);
    }

    @Test
    @DisplayName("processExpiredCampaigns: goal reached → project set to FUNDED")
    void processExpiredCampaigns_setsFunded_whenGoalReached() {
        given(projectRepository.findExpiredApprovedProjects(any()))
                .willReturn(List.of(expiredFunded));
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(donationRepository.findByProject_IdAndPaymentStatus(anyLong(), eq(PaymentStatus.SUCCESS)))
                .willReturn(List.of());

        scheduler.processExpiredCampaigns();

        verify(projectRepository).save(argThat(p ->
                p.getStatus() == ProjectStatus.FUNDED));
        verify(notificationService).notifyCreatorCampaignFunded(expiredFunded);
        verify(refundService, never()).processRefundsForProject(any());
    }

    @Test
    @DisplayName("processExpiredCampaigns: goal not reached → project set to FAILED")
    void processExpiredCampaigns_setsFailed_whenGoalNotReached() {
        given(projectRepository.findExpiredApprovedProjects(any()))
                .willReturn(List.of(expiredFailed));
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(donationRepository.findByProject_IdAndPaymentStatus(anyLong(), eq(PaymentStatus.SUCCESS)))
                .willReturn(List.of());

        scheduler.processExpiredCampaigns();

        verify(projectRepository).save(argThat(p ->
                p.getStatus() == ProjectStatus.FAILED));
        verify(notificationService).notifyCreatorCampaignFailed(expiredFailed);
        verify(refundService).processRefundsForProject(expiredFailed);
    }

    @Test
    @DisplayName("processExpiredCampaigns: backers notified for FUNDED project")
    void processExpiredCampaigns_notifiesBackers_whenFunded() {
        User   backer   = TestDataFactory.backerUser();
        Donation donation = TestDataFactory.successfulDonation(backer, expiredFunded);

        given(projectRepository.findExpiredApprovedProjects(any()))
                .willReturn(List.of(expiredFunded));
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(donationRepository.findByProject_IdAndPaymentStatus(anyLong(), eq(PaymentStatus.SUCCESS)))
                .willReturn(List.of(donation));

        scheduler.processExpiredCampaigns();

        verify(notificationService).notifyBackerCampaignFunded(backer, expiredFunded);
    }

    @Test
    @DisplayName("processExpiredCampaigns: backers notified for FAILED project")
    void processExpiredCampaigns_notifiesBackers_whenFailed() {
        User   backer   = TestDataFactory.backerUser();
        Donation donation = TestDataFactory.successfulDonation(backer, expiredFailed);

        given(projectRepository.findExpiredApprovedProjects(any()))
                .willReturn(List.of(expiredFailed));
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(donationRepository.findByProject_IdAndPaymentStatus(anyLong(), eq(PaymentStatus.SUCCESS)))
                .willReturn(List.of(donation));

        scheduler.processExpiredCampaigns();

        verify(notificationService).notifyBackerCampaignFailed(backer, expiredFailed);
    }

    @Test
    @DisplayName("processExpiredCampaigns: one failure doesn't stop others")
    void processExpiredCampaigns_continuesOnError() {
        given(projectRepository.findExpiredApprovedProjects(any()))
                .willReturn(List.of(expiredFunded, expiredFailed));
        // First save throws, second should still proceed
        given(projectRepository.save(any()))
                .willThrow(new RuntimeException("DB error"))
                .willAnswer(inv -> inv.getArgument(0));
        given(donationRepository.findByProject_IdAndPaymentStatus(anyLong(), any()))
                .willReturn(List.of());

        // Should not throw
        scheduler.processExpiredCampaigns();

        // save attempted twice despite first failure
        verify(projectRepository, times(2)).save(any());
    }
}
