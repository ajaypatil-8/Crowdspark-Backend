// src/main/java/Crowdspark/Crowdspark/controller/GdprController.java
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.DataExportResponse;
import Crowdspark.Crowdspark.dto.DeleteAccountRequest;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.GdprService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "GDPR & Account", description = "Account deletion and personal data export (GDPR Art.17 & Art.20)")
@RestController
@RequestMapping("/auth/me")
@RequiredArgsConstructor
public class GdprController {

    private final GdprService gdprService;
    private final UserService userService;

    /**
     * DELETE /auth/me
     *
     * Permanently deletes the account. Requires current password for confirmation.
     * All PII is anonymised. Financial records are retained for legal compliance.
     * A confirmation email is sent before the email address is wiped.
     */
    @Operation(
        summary = "Delete my account",
        description = """
            Permanently anonymises your account. This cannot be undone.
            
            What happens:
            - All personal data (name, email, phone, address, etc.) is wiped
            - All sessions are invalidated
            - Pending donations are cancelled
            - Comments and updates are soft-deleted ([deleted])
            - Saved projects are removed
            - A confirmation email is sent before your email is wiped
            
            What is kept (legal requirement):
            - Anonymised records of financial transactions (Indian tax law)
            - Project records (for backers of those projects)
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        gdprService.deleteAccount(user.getId(), request);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Your account has been permanently deleted. " +
                         "A confirmation email has been sent.")
                .build());
    }

    /**
     * GET /auth/me/data-export
     *
     * Returns all personal data held about the user as a JSON object.
     * Implements GDPR Article 20 — Right to data portability.
     */
    @Operation(
        summary = "Export my personal data",
        description = """
            Returns all data we hold about you in machine-readable JSON format.
            Includes: profile, donations, created projects, saved projects, comments.
            
            This implements GDPR Article 20 — Right to data portability.
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/data-export")
    public ResponseEntity<ApiResponse<DataExportResponse>> exportData(
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        DataExportResponse export = gdprService.exportData(user.getId());

        return ResponseEntity.ok(ApiResponse.ok(
                "Your data export is ready", export));
    }
}
