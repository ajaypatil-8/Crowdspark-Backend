// src/main/java/Crowdspark/Crowdspark/entity/type/ProjectStatus.java
// CHANGE: Added FUNDED and FAILED statuses.
//   FUNDED  — deadline passed AND currentAmount >= goalAmount (or goal hit mid-campaign)
//   FAILED  — deadline passed AND currentAmount < goalAmount  (backers get refunded)

package Crowdspark.Crowdspark.entity.type;

public enum ProjectStatus {
    DRAFT,
    PENDING,
    APPROVED,
    FUNDED,    // ← NEW: campaign successfully funded
    FAILED,    // ← NEW: deadline passed, goal not reached
    CLOSED,    // kept for backward compat (old manual closes)
    REJECTED
}
