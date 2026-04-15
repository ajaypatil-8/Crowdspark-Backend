package Crowdspark.Crowdspark.dto;
import java.io.Serializable;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RewardTierResponse implements Serializable {
    private Long id;
    private String title;
    private String description;
    private Double minimumAmount;
}