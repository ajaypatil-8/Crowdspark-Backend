// src/main/java/Crowdspark/Crowdspark/entity/type/NotificationType.java
// CHANGE: Added CAMPAIGN_UPDATE
// (replaces Feature #4 version)

package Crowdspark.Crowdspark.entity.type;

public enum NotificationType {
    PROJECT_BACKED,
    PROJECT_APPROVED,
    PROJECT_REJECTED,
    PROJECT_GOAL_REACHED,
    PROJECT_DEADLINE_NEAR,
    CAMPAIGN_FUNDED,
    CAMPAIGN_FAILED,
    CAMPAIGN_UPDATE,       // ← NEW: creator posted an update, notify backers
    PAYOUT_INITIATED,
    PAYOUT_FAILED,
    REFUND_PROCESSED,
    REFUND_FAILED,
    KYC_APPROVED,
    KYC_REJECTED,
    DONATION_CONFIRMED,
    GENERAL
}
