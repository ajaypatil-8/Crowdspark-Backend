package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DonationResponse {

    private Long id;
    private Long userId;
    private Long projectId;
    private Double amount;
    private LocalDateTime createdAt;
}
