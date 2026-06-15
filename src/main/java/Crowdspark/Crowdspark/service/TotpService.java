// src/main/java/Crowdspark/Crowdspark/service/TotpService.java

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.LoginResponse;
import Crowdspark.Crowdspark.dto.TotpSetupResponse;

public interface TotpService {

    /**
     * Step 1 of enable flow.
     * Generates a new secret, stores it on the user (but NOT yet enabled),
     * and returns the otpauth:// URI + plain secret for QR code display.
     * Calling this multiple times regenerates the secret.
     */
    TotpSetupResponse generateSetup(Long userId);

    /**
     * Step 2 of enable flow.
     * User confirms they scanned the QR by providing their first valid code.
     * Marks totpEnabled = true on success.
     * Throws 400 if code is invalid.
     */
    void confirmEnable(Long userId, String code);

    /**
     * Disable 2FA for a user.
     * Requires both a valid TOTP code AND the account password.
     * Clears totpSecret + sets totpEnabled = false.
     */
    void disable(Long userId, String code, String password);

    /**
     * Called from the login endpoint when credentials are valid but 2FA is on.
     * Returns full LoginResponse (accessToken + refreshToken) if code is valid.
     * Throws 401 if pendingToken is expired/invalid.
     * Throws 400 if TOTP code is wrong.
     */
    LoginResponse verifyLoginCode(String pendingToken, String code);

    /**
     * Returns true if the given 6-digit code is currently valid for this secret.
     * Allows ±1 window (30-sec period before/after) to tolerate clock drift.
     */
    boolean isCodeValid(String secret, String code);
}
