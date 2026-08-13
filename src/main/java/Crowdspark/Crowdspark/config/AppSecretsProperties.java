package Crowdspark.Crowdspark.config;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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