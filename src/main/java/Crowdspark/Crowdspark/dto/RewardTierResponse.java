package Crowdspark.Crowdspark.dto;
import java.io.Serializable;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RewardTierResponse implements Serializable {
    private Long id;
    private String title;
    private String description;
    private Double minimumAmount;

    // BUG FIX (Feature #24): these were captured on the request DTO but never
    // returned anywhere, so the frontend had nothing to render even after the
    // entity/DB gained the columns to store them.
    private String estimatedDelivery;
    private Integer limitedQuantity;
    private Integer quantityAvailable;
    /** Convenience for the frontend — true only when a limit was set AND it's been reached. */
    private boolean soldOut;
}