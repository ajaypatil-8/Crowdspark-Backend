// src/main/java/Crowdspark/Crowdspark/dto/RegisterRequest.java
// Feature #25 — Input Validation Hardening  (size/pattern constraints)
// Feature #27 — Password Strength & Entropy  (@ValidPassword added to password)

package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.security.validation.ValidPassword;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Pattern(
        regexp  = "^(?!\\.)(?!.*\\.\\.)[a-zA-Z0-9._]+(?<!\\.)$",
        message = "Username may only contain letters, digits, underscores and dots "
                + "(cannot start or end with a dot, no consecutive dots)"
    )
    private String username;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 255, message = "Email must be 255 characters or less")
    private String email;

    @Pattern(
        regexp  = "^(\\+91)?[6-9]\\d{9}$",
        message = "Enter a valid Indian mobile number"
    )
    private String phoneNumber;

    /**
     * @Size enforces length bounds BEFORE @ValidPassword runs entropy scoring.
     * This avoids bcrypt DoS (100 MB "password" string) even if entropy check
     * somehow passes (it won't, but defence in depth).
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128,
          message = "Password must be between 8 and 128 characters")
    @ValidPassword
    private String password;
}
