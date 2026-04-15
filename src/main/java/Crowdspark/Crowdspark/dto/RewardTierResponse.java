package Crowdspark.Crowdspark.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RewardTierResponse {
    private Long id;
    private String title;
    private String description;
    private Double minimumAmount;
}