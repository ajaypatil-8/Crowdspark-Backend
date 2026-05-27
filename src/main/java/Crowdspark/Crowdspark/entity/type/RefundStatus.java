// src/main/java/Crowdspark/Crowdspark/entity/type/RefundStatus.java
package Crowdspark.Crowdspark.entity.type;

public enum RefundStatus {
    INITIATED,   // refund requested to Razorpay
    COMPLETED,   // money back to backer
    FAILED       // Razorpay refund failed
}
