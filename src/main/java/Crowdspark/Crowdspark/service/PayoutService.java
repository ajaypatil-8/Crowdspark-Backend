// src/main/java/Crowdspark/Crowdspark/service/PayoutService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.PayoutResponse;

import java.util.List;

public interface PayoutService {

    /**
     * Admin initiates a payout for a FUNDED project.
     * Calls Razorpay Payout API and creates a Payout record.
     */
    PayoutResponse initiatePayout(Long projectId, Long adminId);

    /** Get all payouts (admin view) */
    List<PayoutResponse> getAllPayouts();

    /** Get payout details for a specific project */
    PayoutResponse getPayoutByProject(Long projectId);
}
