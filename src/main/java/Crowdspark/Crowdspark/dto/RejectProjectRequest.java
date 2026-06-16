// src/main/java/Crowdspark/Crowdspark/dto/RejectProjectRequest.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • reason: added @Size(min=10, max=1000) — was only @NotBlank;
//             short rejections like "bad" are unhelpful for creators;
//             an unbounded reason string would be persisted in full to DB

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectProjectRequest {

    @NotBlank(message = "Rejection reason is required")
    @Size(
        min     = 10,
        max     = 1000,
        message = "Rejection reason must be between 10 and 1000 characters "
                + "(please give a helpful, actionable explanation)"
    )
    private String reason;
}
