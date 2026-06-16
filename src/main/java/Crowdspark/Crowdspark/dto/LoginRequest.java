// src/main/java/Crowdspark/Crowdspark/dto/LoginRequest.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • identifier: added @Size(max=255) — was only @NotBlank; a huge identifier
//                 string would cause a full DB query + bcrypt attempt
//   • password:   added @Size(max=128) — same denial-of-service concern as above

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email, username or phone number is required")
    @Size(max = 255, message = "Identifier must be 255 characters or less")
    private String identifier;

    @NotBlank(message = "Password is required")
    @Size(max = 128, message = "Password must be 128 characters or less")
    private String password;
}
