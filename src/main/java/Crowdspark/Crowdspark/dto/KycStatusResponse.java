package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KycStatusResponse {

    private Long userId;
    private String username;
    private String email;

    private KycStatus kycStatus;
    private String rejectionReason;    // filled only when REJECTED

    private String panNumber;
    private String panCardImageUrl;

    private String aadhaarNumber;
    private String aadhaarFrontImageUrl;
    private String aadhaarBackImageUrl;

    private String bankName;
    private String maskedBankAccount;  // ****1234
    private String bankIfscCode;
    private String upiId;

    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}