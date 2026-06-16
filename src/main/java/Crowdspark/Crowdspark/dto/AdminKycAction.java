// src/main/java/Crowdspark/Crowdspark/dto/AdminKycAction.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • rejectionReason: added @Size(min=10, max=1000) — was completely unconstrained
//                      (any string length, including a multi-MB payload)

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminKycAction {

    /**
     * Required only when rejecting — must explain exactly what the submitter
     * should fix so they can re-submit successfully.
     */
    @Size(
        min     = 10,
        max     = 1000,
        message = "Rejection reason must be between 10 and 1000 characters"
    )
    private String rejectionReason;
}
