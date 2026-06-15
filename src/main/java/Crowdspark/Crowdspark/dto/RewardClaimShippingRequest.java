package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RewardClaimShippingRequest {

    @NotBlank
    @Size(max = 255)
    private String shippingName;

    @NotBlank
    @Size(max = 1000)
    private String shippingAddress;

    @NotBlank
    @Size(max = 100)
    private String shippingCity;

    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "PIN code must be 6 digits")
    private String shippingPincode;

    @Size(max = 100)
    private String shippingCountry;

    @Size(max = 20)
    private String shippingPhone;
}
