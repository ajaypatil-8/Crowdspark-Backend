package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.CreateDonationRequest;
import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.DonationService;
import Crowdspark.Crowdspark.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/donations")
@RequiredArgsConstructor
public class DonationController {

    private final DonationService donationService;
    private final UserService userService;

    /** Back a project */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<DonationResponse>> donate(
            @Valid @RequestBody CreateDonationRequest request,
            @AuthenticationPrincipal String username
    ) {
        User backer = userService.getByUsername(username);
        DonationResponse response = donationService.donate(request, backer.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /** My donation history */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<DonationResponse>>> myDonations(
            @AuthenticationPrincipal String username
    ) {
        User backer = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(donationService.getMyDonations(backer.getId())));
    }

    /** Donations for a specific project (creator/admin) */
    @PreAuthorize("hasAnyRole('CREATOR','ADMIN')")
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<DonationResponse>>> projectDonations(
            @PathVariable Long projectId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(donationService.getProjectDonations(projectId)));
    }
}
