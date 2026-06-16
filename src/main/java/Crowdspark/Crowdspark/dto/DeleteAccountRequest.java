// src/main/java/Crowdspark/Crowdspark/dto/DeleteAccountRequest.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • password: added @Size(max=128) — was only @NotBlank
//   • reason:   added @Size(max=1000) — was completely unconstrained;
//               an attacker could send a multi-MB reason string to waste I/O

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    /**
     * Current password — required to confirm the user intentionally deletes
     * their account and is not a victim of CSRF or session hijacking.
     */
    @NotBlank(message = "Password confirmation is required to delete your account")
    @Size(max = 128, message = "Password must be 128 characters or less")
    private String password;

    /**
     * Optional deletion reason — stored in aggregated analytics only
     * (never tied back to the user after anonymisation).
     */
    @Size(max = 1000, message = "Reason must be 1000 characters or less")
    private String reason;
}
