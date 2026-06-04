// src/main/java/Crowdspark/Crowdspark/entity/type/AccountStatus.java
// CHANGE: Added DELETED status for GDPR account deletion

package Crowdspark.Crowdspark.entity.type;

public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    BANNED,
    DELETED    // ← NEW: account deleted by user (data anonymised)
}
