// src/test/java/Crowdspark/Crowdspark/service/DonationServiceImplTest.java
//
// AUDIT FIX (Feature #1): this file used to test DonationServiceImpl.donate()
// exclusively — every test in it called donationService.donate(...), the
// method that let a caller mark a donation SUCCESS directly from a
// client-supplied transactionId with zero Razorpay verification. That method
// has been removed (see DonationServiceImpl's class comment for the full
// explanation), and the validation logic it used to duplicate (own-project,
// not-approved, deadline-passed, exceeds-remaining, reward-tier checks) only
// ever really matters on the real flow now, which is createOrder() —
// already covered by PaymentServiceImplTest.
//
// DonationServiceImpl itself is down to two simple read methods, so that's
// what this file tests now.
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.service.impl.DonationServiceImpl;
import Crowdspark.Crowdspark.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("DonationServiceImpl Tests")
class DonationServiceImplTest {

    @Mock DonationRepository donationRepository;

    @InjectMocks DonationServiceImpl donationService;

    private User    backer;
    private Project project;

    @Test
    @DisplayName("getMyDonations returns the backer's donation history, newest first")
    void getMyDonations_returnsMappedHistory() {
        backer  = TestDataFactory.backerUser();
        project = TestDataFactory.approvedProject(TestDataFactory.creatorUser());
        Donation donation = TestDataFactory.successfulDonation(backer, project);

        given(donationRepository.findByBacker_IdOrderByCreatedAtDesc(backer.getId()))
                .willReturn(List.of(donation));

        List<DonationResponse> result = donationService.getMyDonations(backer.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(donation.getId());
        assertThat(result.get(0).getProjectTitle()).isEqualTo(project.getTitle());
        assertThat(result.get(0).getPaymentStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("getMyDonations returns an empty list when the backer has never donated")
    void getMyDonations_returnsEmptyList_whenNoDonations() {
        given(donationRepository.findByBacker_IdOrderByCreatedAtDesc(1L)).willReturn(List.of());

        List<DonationResponse> result = donationService.getMyDonations(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProjectDonations returns all donations for a project")
    void getProjectDonations_returnsMappedList() {
        backer  = TestDataFactory.backerUser();
        project = TestDataFactory.approvedProject(TestDataFactory.creatorUser());
        Donation donation = TestDataFactory.successfulDonation(backer, project);

        given(donationRepository.findByProject_IdOrderByCreatedAtDesc(project.getId()))
                .willReturn(List.of(donation));

        List<DonationResponse> result = donationService.getProjectDonations(project.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBackerUsername()).isEqualTo(backer.getUsername());
        assertThat(result.get(0).getAmount()).isEqualTo(donation.getAmount());
    }
}
