package Crowdspark.Crowdspark.dto;

import lombok.Data;

@Data
public class AdminKycAction {

    // Required only when rejecting — optional for approve
    private String rejectionReason;
}