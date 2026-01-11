package Crowdspark.Crowdspark.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class RewardResponse {
    private Long id;
    private Long projectId;
    private String title;
    private Double minAmount;
    private String description;
}

