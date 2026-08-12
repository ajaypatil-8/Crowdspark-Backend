package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.PaymentOrderResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import Crowdspark.Crowdspark.security.filter.RateLimitFilter;
import Crowdspark.Crowdspark.service.PaymentService;
import Crowdspark.Crowdspark.service.UserService;
import Crowdspark.Crowdspark.util.TestDataFactory;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PaymentController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("PaymentController Tests")
class PaymentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PaymentService paymentService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtAuthenticationFilter jwtFilter;

    @MockitoBean
    RateLimitFilter rateLimitFilter;

    @MockitoBean
    JpaMetamodelMappingContext jpaMappingContext;

    // ─── POST /api/v1/payment/create-order ─────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/payment/create-order → 201 with valid payload")
    void createOrder_returns201_withValidPayload() throws Exception {
        User backer = TestDataFactory.backerUser();

        given(userService.getByUsername(any()))
                .willReturn(backer);

        given(paymentService.createOrder(any(), anyLong()))
                .willReturn(
                        PaymentOrderResponse.builder()
                                .razorpayOrderId("order_abc123")
                                .amountInPaise(100000L)
                                .currency("INR")
                                .razorpayKeyId("rzp_test_stub")
                                .donationId(100L)
                                .projectTitle("Test Campaign")
                                .build()
                );

        Map<String, Object> body = Map.of(
                "projectId", 10,
                "amount", 1000.0
        );

        mockMvc.perform(post("/api/v1/payment/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .principal(() -> "testbacker"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.razorpayOrderId").value("order_abc123"))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.donationId").value(100))
                .andExpect(jsonPath("$.data.amountInPaise").value(100000));
    }

    @Test
    @DisplayName("POST /api/v1/payment/create-order → 400 when amount is missing")
    void createOrder_returns400_whenAmountMissing() throws Exception {
        Map<String, Object> body = Map.of(
                "projectId", 10
        );

        mockMvc.perform(post("/api/v1/payment/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .principal(() -> "testbacker"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payment/create-order → 400 when projectId is missing")
    void createOrder_returns400_whenProjectIdMissing() throws Exception {
        Map<String, Object> body = Map.of(
                "amount", 1000.0
        );

        mockMvc.perform(post("/api/v1/payment/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .principal(() -> "testbacker"))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/v1/payment/verify ──────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/payment/verify → 400 when required fields missing")
    void verify_returns400_whenFieldsMissing() throws Exception {
        Map<String, Object> body = Map.of(
                "donationId", 100
        );

        mockMvc.perform(post("/api/v1/payment/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .principal(() -> "testbacker"))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/v1/payment/receipt/{donationId} ──────────────────────────

    @Test
    @DisplayName("GET /api/v1/payment/receipt/{id} → 200 with the PDF bytes")
    void downloadReceipt_returns200_withPdfBytes() throws Exception {
        User backer = TestDataFactory.backerUser();

        given(userService.getByUsername(any()))
                .willReturn(backer);

        byte[] fakePdf = "%PDF-1.4 fake-content".getBytes();

        given(paymentService.getReceiptPdf(100L, backer.getId()))
                .willReturn(fakePdf);

        mockMvc.perform(get("/api/v1/payment/receipt/100")
                        .principal(() -> "testbacker"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("CrowdSpark_Receipt_100.pdf")
                ))
                .andExpect(content().bytes(fakePdf));
    }
}