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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Razorpay payment gateway — create order and verify payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService    userService;

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create Razorpay order",
            description = "Step 1: Creates a Razorpay order and PENDING donation. Returns order details for checkout.js.",
            security = @SecurityRequirement(name = "bearerAuth"))
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

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verify payment signature",
            description = "Step 2: Called after Razorpay checkout. Verifies HMAC signature and confirms donation.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<DonationResponse>> verify(
            @Valid @RequestBody PaymentVerifyRequest request,
            @AuthenticationPrincipal String username
    ) {
        User backer = userService.getByUsername(username);
        DonationResponse response = paymentService.verifyAndConfirm(request, backer.getId());
        return ResponseEntity.ok(ApiResponse.ok("Payment confirmed successfully", response));
    }

    // AUDIT FIX (Feature #4): server-to-server webhook, called directly by
    // Razorpay's own servers — not by our frontend, and never carries a
    // Bearer token. It is authenticated entirely via the X-Razorpay-Signature
    // header (see PaymentServiceImpl.verifyWebhookSignature), so it's left
    // public in SecurityConfig (permitAll, and CSRF-exempt — see
    // CSRF_EXEMPT_PATHS there) rather than behind @PreAuthorize.
    //
    // Register this URL in the Razorpay Dashboard -> Settings -> Webhooks,
    // subscribed to "payment.captured", and put the secret Razorpay gives you
    // there into razorpay.webhook-secret.
    //
    // IMPORTANT: the request body is captured as a raw String on purpose.
    // Razorpay signs the exact bytes it sent; if this were instead bound to a
    // parsed/re-serialized DTO, the signature could never match.
    @Operation(summary = "Razorpay webhook (server-to-server)",
            description = "Called directly by Razorpay when a payment is captured. Reconciles donations " +
                    "that reached PENDING but whose client never called /verify (e.g. the browser tab " +
                    "closed right after a successful charge).")
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        paymentService.confirmFromWebhook(rawBody, signature);
        // Always 200 on anything we accepted-but-ignored (wrong event type,
        // donation not found yet, etc.) so Razorpay doesn't endlessly retry —
        // confirmFromWebhook only throws for an actually bad/missing signature.
        return ResponseEntity.ok("ok");
    }

    // FIX #10: this endpoint didn't exist — "expose download endpoint" was never done.
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download payment receipt",
            description = "Downloads the tax invoice / payment receipt PDF for a donation. " +
                    "Only the backer who made the donation (or an admin) can access it, " +
                    "and only once the payment has succeeded.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/receipt/{donationId}")
    public ResponseEntity<byte[]> downloadReceipt(
            @PathVariable Long donationId,
            @AuthenticationPrincipal String username
    ) {
        User requester = userService.getByUsername(username);
        byte[] pdf = paymentService.getReceiptPdf(donationId, requester.getId());

        String filename = "CrowdSpark_Receipt_" + donationId + ".pdf";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }
}
