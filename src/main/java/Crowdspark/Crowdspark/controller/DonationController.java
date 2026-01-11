package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.DonateRequest;
import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.service.DonationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class DonationController {

    private static final Logger logger = LoggerFactory.getLogger(DonationController.class);

    private final DonationService donationService;


    @PreAuthorize("hasRole('BACKER')")
    @PostMapping("/projects/{projectId}/donations")
    public ResponseEntity<DonationResponse> donate(
            @PathVariable Long projectId,
            @Valid @RequestBody DonateRequest request
    ) {
        DonationResponse response = donationService.donate(projectId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        logger.info("Donation created: id={}, userId={}, projectId={}, amount={}", response.getId(), response.getUserId(), response.getProjectId(), response.getAmount());

        return ResponseEntity.created(location).body(response);
    }



    @PreAuthorize("isAuthenticated()")
    @GetMapping("/donations/my")
    public ResponseEntity<List<DonationResponse>> myDonations() {
        List<DonationResponse> donations = donationService.myDonations();
        return ResponseEntity.ok(donations);
    }



    @PreAuthorize("hasAnyRole('ADMIN','CREATOR')")
    @GetMapping("/projects/{projectId}/donations")
    public ResponseEntity<List<DonationResponse>> projectDonations(
            @PathVariable Long projectId
    ) {
        List<DonationResponse> donations = donationService.projectDonations(projectId);
        return ResponseEntity.ok(donations);
    }
}
