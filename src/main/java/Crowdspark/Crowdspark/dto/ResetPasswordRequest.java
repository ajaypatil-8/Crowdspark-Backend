// src/main/java/Crowdspark/Crowdspark/dto/ResetPasswordRequest.java
// Feature #27 — Password Strength & Entropy Validation
//
// Replaces the raw Map<String, String> body that /auth/reset-password used to
// consume. Now the endpoint gets full @Valid + @ValidPassword enforcement.

package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.security.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 255, message = "Email must be 255 characters or less")
    private String email;

    @NotBlank(message = "Reset token is required")
    @Size(max = 512, message = "Token must be 512 characters or less")
    private String token;

    /**
     * The new password.
     * Validated by @ValidPassword:
     *   • not in the common-passwords blacklist
     *   • entropy score FAIR or above (36+ bits)
     *   • minimum length enforced by @Size
     */
    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128,
          message = "Password must be between 8 and 128 characters")
    @ValidPassword
    private String password;
}
