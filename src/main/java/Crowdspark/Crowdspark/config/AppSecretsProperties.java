// src/main/java/Crowdspark/Crowdspark/config/AppSecretsProperties.java
// Feature #34 — Secrets management
//
// Binds to the app.secrets.* properties in application.properties (each of
// which is just an alias for an existing ${ENV_VAR} already used elsewhere —
// see that file for the cross-references). @Validated is what actually makes
// Spring run the constraints below during startup binding; without it these
// annotations would be inert.
//
// The point: turn "the app boots fine, then some unrelated feature mysteriously
// fails/crashes the first time it's used, days or weeks later" into "the app
// refuses to start at all, immediately, with a specific message telling you
// exactly which environment variable is missing and why it matters." Every
// message below is deliberately specific about the user-facing consequence,
// not just "must not be blank".
//
// Doesn't replace the existing @Value("${jwt.secret}") etc. injections
// scattered through the codebase (SecurityConfig, JwtUtil, PaymentServiceImpl,
// ...) — those still read the SAME underlying environment variables directly
// and keep working exactly as before. This class exists purely to validate,
// not to become the new way everything reads these values; that would have
// meant touching every one of those call sites, a much larger and riskier
// change than what this feature actually calls for.

package Crowdspark.Crowdspark.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.secrets")
@Validated
@Data
public class AppSecretsProperties {

    @NotBlank(message = "DB_PASSWORD environment variable must be set — the application cannot connect to Postgres without it.")
    private String dbPassword;

    @NotBlank(message = "JWT_SECRET environment variable must be set.")
    @Size(min = 32, message = "JWT_SECRET must be at least 32 characters. jjwt's Keys.hmacShaKeyFor() throws " +
            "WeakKeyException for anything shorter — without this check that crashes on the first login " +
            "attempt with a cryptic error instead of failing here, at startup, with this message.")
    private String jwtSecret;

    @NotBlank(message = "CLOUDINARY_API_KEY environment variable must be set — media uploads (project images/videos, KYC documents) fail without it.")
    private String cloudinaryApiKey;

    @NotBlank(message = "CLOUDINARY_API_SECRET environment variable must be set — media uploads fail without it.")
    private String cloudinaryApiSecret;

    @NotBlank(message = "MAIL_PASSWORD environment variable must be set — OTP emails, receipts, and notifications silently fail to send without it.")
    private String mailPassword;

    @NotBlank(message = "GOOGLE_CLIENT_SECRET environment variable must be set — \"Sign in with Google\" fails without it.")
    private String googleClientSecret;

    @NotBlank(message = "GITHUB_CLIENT_SECRET environment variable must be set — \"Sign in with GitHub\" fails without it.")
    private String githubClientSecret;

    @NotBlank(message = "RAZORPAY_KEY_SECRET environment variable must be set — no donation can be verified as paid without it.")
    private String razorpayKeySecret;


//    @NotBlank(message = "RAZORPAY_WEBHOOK_SECRET environment variable must be set. Without it, a donation from a " +
//            "backer who closes their browser tab right after paying — before the client-side verification call " +
//            "completes — can get stuck in PENDING forever, since the webhook is the only thing that would have caught it.")
//    private String razorpayWebhookSecret;
}
