// src/main/java/Crowdspark/Crowdspark/dto/DeleteAccountRequest.java
package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    /**
     * Current password — required to confirm the user is intentionally
     * deleting their account and not a victim of CSRF or session hijack.
     */
    @NotBlank(message = "Password confirmation is required to delete your account")
    private String password;

    /**
     * Optional reason — stored for internal analytics only (not tied to user after deletion).
     */
    private String reason;
}
