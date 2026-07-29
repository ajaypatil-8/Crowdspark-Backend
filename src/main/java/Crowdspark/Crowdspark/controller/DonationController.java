package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.DonationService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// AUDIT FIX (Feature #1): this controller used to also expose
//     POST /api/v1/donations
// which called DonationServiceImpl.donate() — a leftover, pre-Razorpay stub
// that took a client-supplied `transactionId` string and marked the donation
// PaymentStatus.SUCCESS unconditionally, with zero payment verification.
// Anyone with a valid JWT could "back" any project for any amount for free.
//
// The real, verified flow lives entirely in PaymentController:
//   1. POST /api/v1/payment/create-order  -> creates a Razorpay order + PENDING donation
//   2. POST /api/v1/payment/verify        -> verifies the Razorpay HMAC signature,
//                                            only then marks the donation SUCCESS
//   3. POST /api/v1/payment/webhook       -> server-to-server confirmation from
//                                            Razorpay itself, as a safety net if the
//                                            client never calls step 2
//
// This controller is now read-only: donation history for backers/creators.
// Nothing else in the codebase called donationService.donate(), so removing
// it here is not a breaking change for the frontend (it never called this
// endpoint either) -- it only removes a way to fake a donation over the raw API.
@RestController
@RequestMapping("/api/v1/donations")
@RequiredArgsConstructor
@Tag(name = "Donations", description = "View donation history for backers and creators")
public class DonationController {

    private final DonationService donationService;
    private final UserService userService;

    /** My donation history */
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my donation history", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<DonationResponse>>> myDonations(
            @AuthenticationPrincipal String username
    ) {
        User backer = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(donationService.getMyDonations(backer.getId())));
    }

    /** Donations for a specific project (creator/admin) */
    @PreAuthorize("hasAnyRole('CREATOR','ADMIN')")
    @Operation(summary = "Get donations for a project", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<DonationResponse>>> projectDonations(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(donationService.getProjectDonations(projectId)));
    }
}
