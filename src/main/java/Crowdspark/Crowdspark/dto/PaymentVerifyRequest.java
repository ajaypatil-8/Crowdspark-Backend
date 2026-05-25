// src/main/java/Crowdspark/Crowdspark/dto/PaymentVerifyRequest.java

package Crowdspark.Crowdspark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentVerifyRequest {

    /** Our internal donation id */
    @NotNull(message = "Donation ID is required")
    private Long donationId;

    /** From Razorpay checkout handler: response.razorpay_payment_id */
    @NotBlank(message = "Payment ID is required")
    private String razorpayPaymentId;

    /** From Razorpay checkout handler: response.razorpay_order_id */
    @NotBlank(message = "Order ID is required")
    private String razorpayOrderId;

    /** From Razorpay checkout handler: response.razorpay_signature */
    @NotBlank(message = "Signature is required")
    private String razorpaySignature;
}
