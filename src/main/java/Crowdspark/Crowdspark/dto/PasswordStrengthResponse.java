// src/main/java/Crowdspark/Crowdspark/dto/PasswordStrengthResponse.java
// Feature #27 — Password Strength & Entropy Validation
//
// Returned by GET /auth/password-strength?password=...
// Frontend can call this endpoint (debounced) for server-side confirmation,
// though the TypeScript passwordStrength.ts utility mirrors the same logic
// so client-side feedback is instant without a network round-trip.

package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PasswordStrengthResponse {

    /** 0 = very weak … 4 = very strong */
    private int score;

    /** Human-readable label: VERY_WEAK / WEAK / FAIR / STRONG / VERY_STRONG */
    private String strength;

    /**
     * true  → password would pass @ValidPassword validation
     * false → password would be rejected (score < FAIR or common password)
     */
    private boolean acceptable;

    /** One-sentence feedback the UI can display */
    private String feedback;

    /** Calculated Shannon entropy in bits (informational) */
    private double entropyBits;

    /** true if the password was found in the common-passwords blacklist */
    private boolean commonPassword;
}
