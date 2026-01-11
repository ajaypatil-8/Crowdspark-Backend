package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long donationId;
    private Double amount;
    private PaymentStatus status;
    private String provider;
    private LocalDateTime createdAt;
}

