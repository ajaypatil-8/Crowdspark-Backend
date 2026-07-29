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
     * Step 2 — Verify Razorpay HMAC signature (client-driven, called by the
     * frontend right after checkout.js reports success).
     * On success: marks donation as SUCCESS, updates project.currentAmount,
     * updates backer/creator stats, and fires notifications.
     */
    DonationResponse verifyAndConfirm(PaymentVerifyRequest request, Long backerId);

    /**
     * AUDIT FIX (Feature #4) — server-to-server confirmation from Razorpay's
     * own webhook, independent of whether the browser ever calls
     * verifyAndConfirm(). Verifies the webhook's own HMAC signature (over the
     * raw request body, using razorpay.webhook-secret — NOT the same secret
     * or payload as verifyAndConfirm()'s signature check), then applies the
     * same "mark this donation paid" logic if the event is payment.captured.
     *
     * @param rawBody          the exact, unmodified request body Razorpay sent
     * @param webhookSignature the value of the X-Razorpay-Signature header
     */
    void confirmFromWebhook(String rawBody, String webhookSignature);

    /**
     * FIX #10 — on-demand receipt download.
     * Returns the tax-invoice/payment-receipt PDF for a donation.
     * Only the backer who made the donation (or an admin) may fetch it, and
     * only once the payment has actually succeeded.
     */
    byte[] getReceiptPdf(Long donationId, Long requesterId);
}
