
package Crowdspark.Crowdspark.service;

import java.time.LocalDateTime;

public interface PdfReceiptService {


    byte[] generateReceiptPdf(Long donationId, String backerName, String projectTitle, Double amount,
                              String transactionId, String rewardTierTitle, LocalDateTime paidAt);
}