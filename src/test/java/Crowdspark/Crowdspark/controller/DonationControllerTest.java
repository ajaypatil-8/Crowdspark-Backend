package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import Crowdspark.Crowdspark.security.filter.RateLimitFilter;
import Crowdspark.Crowdspark.service.DonationService;
import Crowdspark.Crowdspark.service.UserService;
import Crowdspark.Crowdspark.util.TestDataFactory;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = DonationController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("DonationController Tests")
class DonationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    DonationService donationService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtAuthenticationFilter jwtFilter;

    @MockitoBean
    RateLimitFilter rateLimitFilter;

    @MockitoBean
    JpaMetamodelMappingContext jpaMappingContext;

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

    // ─── GET /api/v1/donations/my ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/donations/my → 200 with the backer's donation history")
    void myDonations_returns200_withList() throws Exception {
        given(userService.getByUsername(any()))
                .willReturn(TestDataFactory.backerUser());

        given(donationService.getMyDonations(1L))
                .willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/donations/my")
                        .principal(() -> "testbacker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].projectTitle").value("Test Campaign"));
    }

    @Test
    @DisplayName("GET /api/v1/donations/my → 200 with an empty list when the backer has never donated")
    void myDonations_returns200_withEmptyList() throws Exception {
        given(userService.getByUsername(any()))
                .willReturn(TestDataFactory.backerUser());

        given(donationService.getMyDonations(1L))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/donations/my")
                        .principal(() -> "testbacker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ─── GET /api/v1/donations/project/{projectId} ─────────────────────────

    @Test
    @DisplayName("GET /api/v1/donations/project/{id} → 200 with donations for that project")
    void projectDonations_returns200_withList() throws Exception {
        given(donationService.getProjectDonations(10L))
                .willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/donations/project/10")
                        .principal(() -> "testcreator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].backerUsername").value("testbacker"));
    }
}