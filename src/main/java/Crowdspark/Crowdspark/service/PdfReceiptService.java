// src/main/java/Crowdspark/Crowdspark/service/PdfReceiptService.java

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.Donation;

public interface PdfReceiptService {

    byte[] generateReceipt(Donation donation);
}