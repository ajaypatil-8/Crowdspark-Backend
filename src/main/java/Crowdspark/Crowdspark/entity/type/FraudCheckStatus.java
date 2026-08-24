// src/main/java/Crowdspark/Crowdspark/entity/type/FraudCheckStatus.java
// Feature #43 — AI Fraud & Risk Detection

package Crowdspark.Crowdspark.entity.type;

public enum FraudCheckStatus {
    PENDING,    // queued, scan hasn't run yet
    COMPLETED,  // scored successfully — riskScore/riskLevel/reasoning are populated
    FAILED      // Groq call or parsing failed; admin sees "scan unavailable" rather than a wrong score
}
