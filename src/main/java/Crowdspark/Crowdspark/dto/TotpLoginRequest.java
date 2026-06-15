package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TotpLoginRequest {
    /** The short-lived pending token returned by the login endpoint */
    @NotBlank
    private String pendingToken;

    /** 6-digit TOTP code from authenticator app */
    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "Code must be exactly 6 digits")
    private String code;
}