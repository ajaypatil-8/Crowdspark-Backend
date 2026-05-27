// src/main/java/Crowdspark/Crowdspark/entity/type/NotificationType.java
// CHANGE: Add PAYOUT_INITIATED and PAYOUT_FAILED
// (Use the NotificationType.java from Feature #2 and add these 2 lines)

package Crowdspark.Crowdspark.entity.type;

public enum NotificationType {
    PROJECT_BACKED,
    PROJECT_APPROVED,
    PROJECT_REJECTED,
    PROJECT_GOAL_REACHED,
    PROJECT_DEADLINE_NEAR,
    CAMPAIGN_FUNDED,       // from Feature #2
    CAMPAIGN_FAILED,       // from Feature #2
    PAYOUT_INITIATED,      // ← NEW: payout sent to creator
    PAYOUT_FAILED,         // ← NEW: payout failed, admin notified
    KYC_APPROVED,
    KYC_REJECTED,
    DONATION_CONFIRMED,
    GENERAL
}
