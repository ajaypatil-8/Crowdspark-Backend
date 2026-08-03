// src/main/java/Crowdspark/Crowdspark/dto/PaymentVerifyRequest.java

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentVerifyRequest {

    /** Our internal donation id */
    @NotNull(message = "Donation ID is required")
    private Long donationId;

    /** From Razorpay checkout handler: response.razorpay_payment_id */
    @NotBlank(message = "Payment ID is required")
    @Size(max = 255, message = "Payment ID must be 255 characters or less")
    private String razorpayPaymentId;

    /** From Razorpay checkout handler: response.razorpay_order_id */
    @NotBlank(message = "Order ID is required")
    @Size(max = 255, message = "Order ID must be 255 characters or less")
    private String razorpayOrderId;

    /** From Razorpay checkout handler: response.razorpay_signature */
    @NotBlank(message = "Signature is required")
    @Size(max = 512, message = "Signature must be 512 characters or less")
    private String razorpaySignature;
}
