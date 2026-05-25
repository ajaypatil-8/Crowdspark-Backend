// src/main/java/Crowdspark/Crowdspark/service/PaymentService.java

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.dto.PaymentOrderRequest;
import Crowdspark.Crowdspark.dto.PaymentOrderResponse;
import Crowdspark.Crowdspark.dto.PaymentVerifyRequest;

public interface PaymentService {

    /**
     * Step 1 — Create a Razorpay order and a PENDING donation.
     * Returns order details the frontend needs to open Razorpay checkout.
     */
    PaymentOrderResponse createOrder(PaymentOrderRequest request, Long backerId);

    /**
     * Step 2 — Verify Razorpay HMAC signature.
     * On success: marks donation as SUCCESS, updates project.currentAmount,
     * updates backer/creator stats, and fires notifications.
     */
    DonationResponse verifyAndConfirm(PaymentVerifyRequest request, Long backerId);
}
