// src/main/java/Crowdspark/Crowdspark/service/EmailService.java
package Crowdspark.Crowdspark.service;

/**
 * Feature #9 — HTML email templates.
 *
 * All methods are fire-and-forget: implementations are expected to be
 * @Async so callers (controllers/services inside a @Transactional method)
 * never block on SMTP latency.
 */
public interface EmailService {

    /**
     * Numeric OTP code, used for creator-upgrade / KYC identity re-verification.
     * @param validityMinutes how long the OTP is valid for — shown in the email
     *                        so the copy always matches the real expiry.
     */
    void sendOtpEmail(String toEmail, String name, String otp, int validityMinutes);

    /** Unstyled plain-text email — unchanged, still used for links/alerts not covered by Feature #9. */
    void sendSimpleEmail(String toEmail, String subject, String body);

    /** Sent once, right after a new account is created. */
    void sendWelcomeEmail(String toEmail, String name);

    void sendEmailWithAttachment(
            String toEmail,
            String subject,
            String htmlBody,
            byte[] attachment,
            String fileName
    );

    /** Sent to the creator when an admin approves their project. */
    void sendCampaignApprovedEmail(String toEmail, String creatorName, String projectTitle, Long projectId);

    /** Sent to the creator when an admin rejects their project, with the reason. */
    void sendCampaignRejectedEmail(String toEmail, String creatorName, String projectTitle, String reason);

    /** Sent to the creator when the deadline scheduler marks their project FUNDED. */
    void sendCampaignFundedEmail(String toEmail, String creatorName, String projectTitle, Long projectId,
                                 Double raisedAmount, Double goalAmount);

    /** Sent to a backer right after a donation is confirmed as paid (Razorpay-verified or direct). */
    void sendBackerReceiptEmail(String toEmail, String backerName, String projectTitle, Long projectId,
                                Double amount, String transactionId, String rewardTierTitle,
                                java.time.LocalDateTime paidAt);
}