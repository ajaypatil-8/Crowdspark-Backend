package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.DonationResponse;

import java.util.List;

// AUDIT FIX (Feature #1): donate(CreateDonationRequest, Long) removed.
// It let a caller mark a donation SUCCESS directly from a client-supplied
// transactionId, bypassing Razorpay entirely. Confirmed donations are now
// only ever created via PaymentService (createOrder -> verifyAndConfirm, or
// the /payment/webhook path), which requires a real, signature-verified
// Razorpay payment before a donation is ever marked SUCCESS.
public interface DonationService {

    List<DonationResponse> getMyDonations(Long backerId);

    List<DonationResponse> getProjectDonations(Long projectId);
}
