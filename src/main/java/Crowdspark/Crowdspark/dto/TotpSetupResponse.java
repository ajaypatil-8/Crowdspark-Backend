// ─── src/main/java/Crowdspark/Crowdspark/dto/TotpSetupResponse.java ──────────

package Crowdspark.Crowdspark.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TotpSetupResponse {
    /** The otpauth:// URI — pass to a QR library on the frontend */
    private String otpauthUri;
    /** Plain base32 secret — shown as text backup for manual entry */
    private String secret;
    /** Human-readable issuer shown in authenticator app */
    private String issuer;
    /** The account label shown in authenticator app */
    private String accountName;
}
