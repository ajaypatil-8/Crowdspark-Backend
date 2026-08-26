// src/main/java/Crowdspark/Crowdspark/entity/type/KycCheckStatus.java
// Feature #44 — AI KYC Document Validation

package Crowdspark.Crowdspark.entity.type;

public enum KycCheckStatus {
    PENDING,    // queued, scan hasn't run yet
    COMPLETED,  // checked successfully — readable/tamperingSuspected/summary populated
    FAILED      // Groq call or parsing failed; admin sees "scan unavailable" rather than a wrong result
}
