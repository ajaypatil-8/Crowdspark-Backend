// src/main/java/Crowdspark/Crowdspark/dto/FcmSubscribeRequest.java

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FcmSubscribeRequest {

    @NotBlank(message = "FCM token is required")
    private String token;

    /** Optional browser/OS hint for debugging, e.g. "Chrome/Windows" */
    @Size(max = 100)
    private String deviceHint;
}
