// src/test/java/Crowdspark/Crowdspark/controller/DonationControllerTest.java
// Feature #13: "MockMvc tests for key endpoints" — Auth/Payment/Project controllers
// already had one each; DonationController had none.
//
// AUDIT FIX (Feature #1): the three tests that used to live here
// (donate_returns201_withValidPayload, donate_returns400_whenProjectIdMissing,
// donate_returns400_whenAmountBelowMinimum) covered POST /api/v1/donations,
// which called DonationServiceImpl.donate() — the endpoint that let a caller
// mark a donation SUCCESS directly from a client-supplied transactionId with
// no Razorpay verification at all. That endpoint (and the donate() method
// itself) has been removed entirely; see DonationController's class comment.
// The equivalent coverage for "does a donation actually need a real,
// verified payment" now lives in PaymentServiceImplTest (verifyAndConfirm's
// signature and order-ID checks).
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import Crowdspark.Crowdspark.service.DonationService;
import Crowdspark.Crowdspark.service.UserService;
import Crowdspark.Crowdspark.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = DonationController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("DonationController Tests")
class DonationControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean DonationService         donationService;
    @MockBean UserService              userService;
    @MockBean JwtAuthenticationFilter jwtFilter;

    private DonationResponse sampleResponse() {
        return DonationResponse.builder()
                .id(500L)
                .projectId(10L)
                .projectTitle("Test Campaign")
                .backerId(1L)
                .backerUsername("testbacker")
                .amount(5_000.0)
                .paymentStatus("SUCCESS")
                .transactionId("pay_test123")
                .createdAt(LocalDateTime.now())
                .paidAt(LocalDateTime.now())
                .build();
    }

    // ─── GET /api/v1/donations/my ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/donations/my → 200 with the backer's donation history")
    void myDonations_returns200_withList() throws Exception {
        given(userService.getByUsername(any())).willReturn(TestDataFactory.backerUser());
        given(donationService.getMyDonations(1L)).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/donations/my")
                        .principal(() -> "testbacker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].projectTitle").value("Test Campaign"));
    }

    @Test
    @DisplayName("GET /api/v1/donations/my → 200 with an empty list when the backer has never donated")
    void myDonations_returns200_withEmptyList() throws Exception {
        given(userService.getByUsername(any())).willReturn(TestDataFactory.backerUser());
        given(donationService.getMyDonations(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/donations/my")
                        .principal(() -> "testbacker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ─── GET /api/v1/donations/project/{projectId} ────────────────────────────

    @Test
    @DisplayName("GET /api/v1/donations/project/{id} → 200 with donations for that project")
    void projectDonations_returns200_withList() throws Exception {
        given(donationService.getProjectDonations(10L)).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/donations/project/10")
                        .principal(() -> "testcreator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].backerUsername").value("testbacker"));
    }
}
