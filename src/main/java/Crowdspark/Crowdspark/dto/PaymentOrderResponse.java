// src/main/java/Crowdspark/Crowdspark/dto/PaymentOrderResponse.java

package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentOrderResponse {

    /** Razorpay order id — passed to checkout.js as `order_id` */
    private String razorpayOrderId;

    /** Amount in PAISE (rupees × 100) — passed to checkout.js as `amount` */
    private Long amountInPaise;

    /** Always "INR" */
    private String currency;

    /** Your Razorpay Key ID — passed to checkout.js as `key` */
    private String razorpayKeyId;

    /** Our internal donation id (PENDING at this point) */
    private Long donationId;

    /** Project title — for the checkout modal description */
    private String projectTitle;
}
