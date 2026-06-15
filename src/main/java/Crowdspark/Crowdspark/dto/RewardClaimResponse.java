package Crowdspark.Crowdspark.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RewardClaimResponse {

    private Long   id;
    private Long   donationId;
    private Long   backerId;
    private String backerName;
    private String backerUsername;
    private String backerProfileImageUrl;

    private Long   projectId;
    private String projectTitle;

    private Long   rewardTierId;
    private String rewardTierTitle;
    private Double rewardTierMinAmount;
    private Double donationAmount;

    /** PENDING | PROCESSING | SHIPPED | FULFILLED | CANCELLED */
    private String status;

    // Shipping details (backer-supplied)
    private String shippingName;
    private String shippingAddress;
    private String shippingCity;
    private String shippingPincode;
    private String shippingCountry;
    private String shippingPhone;
    private boolean shippingProvided;

    // Fulfillment details (creator-supplied)
    private String trackingNumber;
    private String fulfillmentNote;

    private LocalDateTime claimedAt;
    private LocalDateTime fulfilledAt;
}