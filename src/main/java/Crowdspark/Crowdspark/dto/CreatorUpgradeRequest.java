package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreatorUpgradeRequest {

    // ─────────────────────────────────────────
    // KYC DOCUMENTS
    // Images are uploaded to Cloudinary FIRST from frontend,
    // then only the URL is sent here — we never receive file bytes
    // ─────────────────────────────────────────

    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}", message = "Invalid PAN format")
    private String panNumber;

    @NotBlank(message = "PAN card image URL is required")
    private String panCardImageUrl;

    private String panCardImagePublicId;

    @NotBlank(message = "Aadhaar number is required")
    @Pattern(regexp = "\\d{4}-\\d{4}-\\d{4}", message = "Aadhaar must be in format XXXX-XXXX-XXXX")
    private String aadhaarNumber;

    @NotBlank(message = "Aadhaar front image URL is required")
    private String aadhaarFrontImageUrl;

    private String aadhaarFrontPublicId;

    @NotBlank(message = "Aadhaar back image URL is required")
    private String aadhaarBackImageUrl;

    private String aadhaarBackPublicId;

    // ─────────────────────────────────────────
    // BANK DETAILS
    // ─────────────────────────────────────────
    @NotBlank(message = "Account holder name is required")
    private String bankAccountHolderName;

    @NotBlank(message = "Bank account number is required")
    private String bankAccountNumber;

    @NotBlank(message = "IFSC code is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC code")
    private String bankIfscCode;

    @NotBlank(message = "Bank name is required")
    private String bankName;

    private String bankBranchName;

    // ─────────────────────────────────────────
    // UPI
    // ─────────────────────────────────────────
    @NotBlank(message = "UPI ID is required")
    @Pattern(regexp = "^[\\w.\\-_]+@[a-zA-Z]+$", message = "Invalid UPI ID format")
    private String upiId;

    // ─────────────────────────────────────────
    // OTP VERIFICATION
    // User must verify email OTP before upgrade is processed
    // ─────────────────────────────────────────
    @NotBlank(message = "OTP is required")
    private String otp;
}