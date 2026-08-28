// src/main/java/Crowdspark/Crowdspark/entity/type/ModerationStatus.java
// Feature #45 — AI Content Moderation

package Crowdspark.Crowdspark.entity.type;

public enum ModerationStatus {
    PENDING,    // queued, scan hasn't run yet
    CLEAR,      // scanned, no policy violation found
    FLAGGED,    // scanned, violation found — comments get auto-hidden, projects stay advisory
    FAILED      // Groq call or parsing failed
}
