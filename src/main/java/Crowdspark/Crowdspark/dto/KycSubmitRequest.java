package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class KycSubmitRequest {


    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}", message = "Invalid PAN format e.g. ABCDE1234F")
    private String panNumber;

    @NotBlank(message = "PAN card image URL is required")
    private String panCardImageUrl;

    @NotBlank(message = "PAN card image public ID is required")
    private String panCardImagePublicId;


    @NotBlank(message = "Aadhaar number is required")
    @Pattern(regexp = "\\d{4}-\\d{4}-\\d{4}", message = "Aadhaar must be in format XXXX-XXXX-XXXX")
    private String aadhaarNumber;

    @NotBlank(message = "Aadhaar front image URL is required")
    private String aadhaarFrontImageUrl;

    @NotBlank(message = "Aadhaar front image public ID is required")
    private String aadhaarFrontPublicId;

    @NotBlank(message = "Aadhaar back image URL is required")
    private String aadhaarBackImageUrl;

    @NotBlank(message = "Aadhaar back image public ID is required")
    private String aadhaarBackPublicId;


    @NotBlank(message = "Account holder name is required")
    private String bankAccountHolderName;

    @NotBlank(message = "Bank account number is required")
    private String bankAccountNumber;

    @NotBlank(message = "IFSC code is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC code e.g. HDFC0001234")
    private String bankIfscCode;

    @NotBlank(message = "Bank name is required")
    private String bankName;

    private String bankBranchName;


    @NotBlank(message = "UPI ID is required")
    @Pattern(regexp = "^[\\w.\\-_]+@[a-zA-Z]+$", message = "Invalid UPI ID e.g. name@upi")
    private String upiId;
}