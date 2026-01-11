package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data

public class DonateRequest {
    @NotNull(message = "Donation amount is required")
    @Positive(message = "Donation amount must be greater than zero")
    private Double amount;
}
