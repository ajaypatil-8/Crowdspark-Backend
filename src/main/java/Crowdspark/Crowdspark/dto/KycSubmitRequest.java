// src/main/java/Crowdspark/Crowdspark/dto/KycSubmitRequest.java
// Feature #25 — Input Validation Hardening
// Changes:
//   • All image URL / public-id / bank fields: added @Size(max=255) — these
//     map 1:1 to kyc_documents' varchar(255) columns and had no bound at all,
//     so an oversized value crashed the insert with a raw DB truncation error
//     instead of a clean 400.
//   • upiId: added @Size(max=255) — its @Pattern's "+" is unbounded, so
//     "aaaa...(10,000 chars)...a@ab" still matched the regex.
//   • accountHolderName/bankAccountNumber/bankName/bankBranchName: added
//     @Size(max=255) for the same reason; bankBranchName additionally kept
//     optional (no @NotBlank) as it already was.

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KycSubmitRequest {


    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}", message = "Invalid PAN format e.g. ABCDE1234F")
    private String panNumber;

    @NotBlank(message = "PAN card image URL is required")
    @Size(max = 255, message = "PAN card image URL must be 255 characters or less")
    private String panCardImageUrl;

    @NotBlank(message = "PAN card image public ID is required")
    @Size(max = 255, message = "PAN card image public ID must be 255 characters or less")
    private String panCardImagePublicId;


    @NotBlank(message = "Aadhaar number is required")
    @Pattern(regexp = "\\d{4}-\\d{4}-\\d{4}", message = "Aadhaar must be in format XXXX-XXXX-XXXX")
    private String aadhaarNumber;

    @NotBlank(message = "Aadhaar front image URL is required")
    @Size(max = 255, message = "Aadhaar front image URL must be 255 characters or less")
    private String aadhaarFrontImageUrl;

    @NotBlank(message = "Aadhaar front image public ID is required")
    @Size(max = 255, message = "Aadhaar front image public ID must be 255 characters or less")
    private String aadhaarFrontPublicId;

    @NotBlank(message = "Aadhaar back image URL is required")
    @Size(max = 255, message = "Aadhaar back image URL must be 255 characters or less")
    private String aadhaarBackImageUrl;

    @NotBlank(message = "Aadhaar back image public ID is required")
    @Size(max = 255, message = "Aadhaar back image public ID must be 255 characters or less")
    private String aadhaarBackPublicId;


    @NotBlank(message = "Account holder name is required")
    @Size(max = 255, message = "Account holder name must be 255 characters or less")
    private String bankAccountHolderName;

    @NotBlank(message = "Bank account number is required")
    @Size(max = 255, message = "Bank account number must be 255 characters or less")
    private String bankAccountNumber;

    @NotBlank(message = "IFSC code is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC code e.g. HDFC0001234")
    private String bankIfscCode;

    @NotBlank(message = "Bank name is required")
    @Size(max = 255, message = "Bank name must be 255 characters or less")
    private String bankName;

    @Size(max = 255, message = "Bank branch name must be 255 characters or less")
    private String bankBranchName;


    @NotBlank(message = "UPI ID is required")
    @Size(max = 255, message = "UPI ID must be 255 characters or less")
    @Pattern(regexp = "^[\\w.\\-_]+@[a-zA-Z]+$", message = "Invalid UPI ID e.g. name@upi")
    private String upiId;
}