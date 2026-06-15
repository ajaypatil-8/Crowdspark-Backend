// src/main/java/Crowdspark/Crowdspark/entity/type/RewardClaimStatus.java

package Crowdspark.Crowdspark.entity.type;

public enum RewardClaimStatus {
    PENDING,      // donation confirmed, creator hasn't started yet
    PROCESSING,   // creator acknowledged
    SHIPPED,      // physical reward dispatched
    FULFILLED,    // reward fully delivered
    CANCELLED     // donation refunded; reward no longer owed
}
