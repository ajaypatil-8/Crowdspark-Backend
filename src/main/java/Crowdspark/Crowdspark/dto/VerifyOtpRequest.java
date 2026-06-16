// src/main/java/Crowdspark/Crowdspark/dto/VerifyOtpRequest.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • otp: was completely un-annotated (no @NotBlank, no length, no pattern);
//           a null or empty value would cause a NullPointerException in the service.
//           Now requires a 6-digit numeric OTP to match what we actually send.

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "OTP is required")
    @Pattern(
        regexp  = "^[0-9]{6}$",
        message = "OTP must be exactly 6 digits"
    )
    private String otp;
}
