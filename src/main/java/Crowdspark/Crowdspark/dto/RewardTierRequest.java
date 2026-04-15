package Crowdspark.Crowdspark.dto;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RewardTierRequest {
    @NotBlank private String title;
    private String description;
    @NotNull @Positive private Double minimumAmount;
}