// src/main/java/Crowdspark/Crowdspark/entity/type/PayoutStatus.java
package Crowdspark.Crowdspark.entity.type;

public enum PayoutStatus {
    INITIATED,    // Admin clicked "Initiate Payout", record created
    PROCESSING,   // Razorpay payout queued/in transit
    COMPLETED,    // Money reached creator
    FAILED        // Payout failed (invalid UPI, network, etc.)
}
