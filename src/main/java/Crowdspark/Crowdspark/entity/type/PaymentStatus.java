// src/main/java/Crowdspark/Crowdspark/entity/type/PaymentStatus.java
// CHANGE: Added REFUNDED status

package Crowdspark.Crowdspark.entity.type;

public enum PaymentStatus {
    PENDING,    // order created, payment not done yet
    SUCCESS,    // payment confirmed
    FAILED,     // payment failed
    REFUNDED    // ← NEW: refund processed after campaign failed
}
