// src/main/java/Crowdspark/Crowdspark/entity/type/NotificationType.java
// CHANGE: Added REFUND_PROCESSED and REFUND_FAILED
// (replaces the Feature #3 version of this file)

package Crowdspark.Crowdspark.entity.type;

public enum NotificationType {
    PROJECT_BACKED,
    PROJECT_APPROVED,
    PROJECT_REJECTED,
    PROJECT_GOAL_REACHED,
    PROJECT_DEADLINE_NEAR,
    CAMPAIGN_FUNDED,       // Feature #2
    CAMPAIGN_FAILED,       // Feature #2
    PAYOUT_INITIATED,      // Feature #3
    PAYOUT_FAILED,         // Feature #3
    REFUND_PROCESSED,      // ← NEW: refund successfully sent to backer
    REFUND_FAILED,         // ← NEW: refund failed, backer notified to contact support
    KYC_APPROVED,
    KYC_REJECTED,
    DONATION_CONFIRMED,
    GENERAL
}
