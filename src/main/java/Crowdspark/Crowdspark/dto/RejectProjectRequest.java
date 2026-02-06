package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectProjectRequest {

    @NotBlank
    private String reason;
}
