// src/main/java/Crowdspark/Crowdspark/service/GdprService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.DataExportResponse;
import Crowdspark.Crowdspark.dto.DeleteAccountRequest;

public interface GdprService {

    /**
     * Permanently anonymises a user's personal data.
     * - Verifies their current password before proceeding.
     * - Replaces all PII with anonymous placeholders.
     * - Cancels PENDING donations, revokes tokens.
     * - Keeps financial records (SUCCESS donations) for legal compliance.
     * - Soft-deletes comments and campaign updates.
     * - Sends a final confirmation email before wiping the email address.
     */
    void deleteAccount(Long userId, DeleteAccountRequest request);

    /**
     * Exports all personal data held about the user as a JSON-serialisable DTO.
     * Implements GDPR Art. 20 — Right to data portability.
     */
    DataExportResponse exportData(Long userId);
}
