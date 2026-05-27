// src/main/java/Crowdspark/Crowdspark/entity/type/NotificationType.java
// CHANGE: Added CAMPAIGN_FUNDED and CAMPAIGN_FAILED for scheduler notifications

package Crowdspark.Crowdspark.entity.type;

public enum NotificationType {
    PROJECT_BACKED,         // creator: someone backed your project
    PROJECT_APPROVED,       // creator: admin approved your project
    PROJECT_REJECTED,       // creator: admin rejected your project
    PROJECT_GOAL_REACHED,   // creator: project hit funding goal mid-campaign
    PROJECT_DEADLINE_NEAR,  // creator: 3 days left
    CAMPAIGN_FUNDED,        // ← NEW: deadline passed, goal reached — creator + backers
    CAMPAIGN_FAILED,        // ← NEW: deadline passed, goal not reached — creator + backers
    KYC_APPROVED,           // user: KYC approved
    KYC_REJECTED,           // user: KYC rejected
    DONATION_CONFIRMED,     // backer: your donation was confirmed
    GENERAL                 // misc admin message
}
