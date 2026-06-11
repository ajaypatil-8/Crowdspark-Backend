// src/main/java/Crowdspark/Crowdspark/entity/type/NotificationType.java
// CHANGE: Added COMMENT_POSTED and COMMENT_REPLIED
// (replaces Feature #5 version)

package Crowdspark.Crowdspark.entity.type;

public enum NotificationType {
    PROJECT_BACKED,
    PROJECT_APPROVED,
    PROJECT_REJECTED,
    PROJECT_GOAL_REACHED,
    PROJECT_DEADLINE_NEAR,
    CAMPAIGN_FUNDED,
    CAMPAIGN_FAILED,
    CAMPAIGN_UPDATE,
    COMMENT_POSTED,
    COMMENT_REPLIED,
    NEW_FOLLOWER,
    MILESTONE_COMPLETED ,
    PAYOUT_INITIATED,
    PAYOUT_FAILED,
    REFUND_PROCESSED,
    REFUND_FAILED,
    KYC_APPROVED,
    KYC_REJECTED,
    DONATION_CONFIRMED,
    GENERAL
}
