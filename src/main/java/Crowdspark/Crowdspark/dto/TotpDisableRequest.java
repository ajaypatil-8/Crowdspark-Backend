package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TotpDisableRequest {
    /** 6-digit TOTP code to confirm identity before disabling */
    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "Code must be exactly 6 digits")
    private String code;

    /** Current account password — second confirmation factor */
    @NotBlank
    private String password;
}
