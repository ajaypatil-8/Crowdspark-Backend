// src/main/java/Crowdspark/Crowdspark/controller/PaymentController.java

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.dto.PaymentOrderRequest;
import Crowdspark.Crowdspark.dto.PaymentOrderResponse;
import Crowdspark.Crowdspark.dto.PaymentVerifyRequest;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.PaymentService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService    userService;

    /**
     * STEP 1 — Backer clicks "Back this project"
     * Creates a Razorpay order and a PENDING donation.
     * Returns order details that the frontend passes to Razorpay checkout.js.
     *
     * POST /api/payment/create-order
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(
            @Valid @RequestBody PaymentOrderRequest request,
            @AuthenticationPrincipal String username
    ) {
        User backer = userService.getByUsername(username);
        PaymentOrderResponse response = paymentService.createOrder(request, backer.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    /**
     * STEP 2 — Called by frontend after Razorpay checkout succeeds.
     * Verifies HMAC signature, marks donation SUCCESS, updates project totals.
     *
     * POST /api/payment/verify
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<DonationResponse>> verify(
            @Valid @RequestBody PaymentVerifyRequest request,
            @AuthenticationPrincipal String username
    ) {
        User backer = userService.getByUsername(username);
        DonationResponse response = paymentService.verifyAndConfirm(request, backer.getId());
        return ResponseEntity.ok(ApiResponse.ok("Payment confirmed successfully", response));
    }
}
