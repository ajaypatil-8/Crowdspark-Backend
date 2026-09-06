// src/main/java/Crowdspark/Crowdspark/service/EmailService.java
package Crowdspark.Crowdspark.service;

public interface EmailService {



    void sendOtpEmail(String toEmail, String name, String otp, int validityMinutes);

    void sendSimpleEmail(String toEmail, String subject, String body);


    void sendWelcomeEmail(String toEmail, String name);


    void sendCampaignApprovedEmail(String toEmail, String creatorName, String projectTitle, Long projectId);


    void sendCampaignRejectedEmail(String toEmail, String creatorName, String projectTitle, String reason);


    void sendCampaignFundedEmail(String toEmail, String creatorName, String projectTitle, Long projectId,
                                 Double raisedAmount, Double goalAmount);


    void sendBackerReceiptEmail(String toEmail, String backerName, String projectTitle, Long projectId,
                                Long donationId, Double amount, String transactionId, String rewardTierTitle,
                                java.time.LocalDateTime paidAt);

    /** Feature #48 — one digest per creator listing every live campaign's
     *  weekly insight, rather than one email per campaign. */
    void sendWeeklyInsightDigestEmail(String toEmail, String creatorName,
                                       java.util.List<Crowdspark.Crowdspark.dto.WeeklyInsightItem> items);
}