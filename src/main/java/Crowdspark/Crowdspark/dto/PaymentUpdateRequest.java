package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentUpdateRequest {

    @NotNull(message = "donationId is required")
    private Long donationId;

    @NotNull(message = "status is required")
    private PaymentStatus status;
}
