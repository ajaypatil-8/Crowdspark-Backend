// src/main/java/Crowdspark/Crowdspark/service/impl/EmailServiceImpl.java
// Feature #9 — HTML email templates (Thymeleaf)
// Feature #10 — sendBackerReceiptEmail also generates a PDF receipt
// (via PdfReceiptService) and attaches it to the same email.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.service.EmailService;
import Crowdspark.Crowdspark.service.PdfReceiptService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final PdfReceiptService pdfReceiptService; // ← Feature #10

    public EmailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine, PdfReceiptService pdfReceiptService) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.pdfReceiptService = pdfReceiptService;
    }

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.mail.from-name:CrowdSpark}")
    private String fromName;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    // AUDIT FIX: pinned to Locale.ENGLISH for consistency with the same fix in
    // PdfReceiptServiceImpl — an HTML email can render any script fine, so this
    // was never a crash risk here, but a receipt should show a predictable
    // date regardless of the server JVM's default locale.
    private static final DateTimeFormatter RECEIPT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a", Locale.ENGLISH);

    // ─────────────────────────────────────────────────────────────────────────
    // OTP — numeric code (creator upgrade / KYC re-verification)
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Async("emailTaskExecutor")
    public void sendOtpEmail(String toEmail, String name, String otp, int validityMinutes) {
        String safeName = nullSafe(name, "there");

        Context ctx = baseContext();
        ctx.setVariable("name", safeName);
        ctx.setVariable("otp", otp);
        ctx.setVariable("validityMinutes", validityMinutes);

        String plainText = "Hi " + safeName + ",\n\nYour CrowdSpark verification code is: " + otp
                + "\nThis code expires in " + validityMinutes + " minutes.\n\n"
                + "If you didn't request this, you can safely ignore this email.\n\nTeam CrowdSpark";

        sendHtmlEmail(toEmail, "Your CrowdSpark verification code: " + otp, "otp", ctx, plainText);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Plain text — unchanged. Still used by call sites outside Feature #9's scope
    // (verify-email link, reset-password link, KYC decisions, contact replies,
    // GDPR deletion confirmation, refresh-token-theft alert).
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Async("emailTaskExecutor")
    public void sendSimpleEmail(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Welcome — sent once, right after registration
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Async("emailTaskExecutor")
    public void sendWelcomeEmail(String toEmail, String name) {
        String safeName = nullSafe(name, "there");
        String exploreUrl = frontendUrl + "/explore";
        String createCampaignUrl = frontendUrl + "/dashboard/create-campaign";

        Context ctx = baseContext();
        ctx.setVariable("name", safeName);
        ctx.setVariable("exploreUrl", exploreUrl);
        ctx.setVariable("createCampaignUrl", createCampaignUrl);

        String plainText = "Hi " + safeName + ",\n\nWelcome to CrowdSpark! Discover projects worth backing at "
                + exploreUrl + ", or start your own campaign at " + createCampaignUrl + ".\n\nTeam CrowdSpark";

        sendHtmlEmail(toEmail, "Welcome to CrowdSpark, " + safeName + "!", "welcome", ctx, plainText);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Campaign approved
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Async("emailTaskExecutor")
    public void sendCampaignApprovedEmail(String toEmail, String creatorName, String projectTitle, Long projectId) {
        String safeName = nullSafe(creatorName, "there");
        String projectUrl = frontendUrl + "/projects/" + projectId;

        Context ctx = baseContext();
        ctx.setVariable("creatorName", safeName);
        ctx.setVariable("projectTitle", projectTitle);
        ctx.setVariable("projectUrl", projectUrl);

        String plainText = "Hi " + safeName + ",\n\nGreat news — \"" + projectTitle
                + "\" has been approved and is now live on CrowdSpark.\n\nView it here: " + projectUrl
                + "\n\nTeam CrowdSpark";

        sendHtmlEmail(toEmail, "\"" + projectTitle + "\" is live on CrowdSpark", "campaign-approved", ctx, plainText);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Campaign rejected
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Async("emailTaskExecutor")
    public void sendCampaignRejectedEmail(String toEmail, String creatorName, String projectTitle, String reason) {
        String safeName = nullSafe(creatorName, "there");
        String safeReason = nullSafe(reason, "No specific reason was provided.");
        String dashboardUrl = frontendUrl + "/dashboard/my-campaigns";

        Context ctx = baseContext();
        ctx.setVariable("creatorName", safeName);
        ctx.setVariable("projectTitle", projectTitle);
        ctx.setVariable("reason", safeReason);
        ctx.setVariable("dashboardUrl", dashboardUrl);

        String plainText = "Hi " + safeName + ",\n\n\"" + projectTitle + "\" was not approved.\n\nReason: "
                + safeReason + "\n\nYou can review and resubmit here: " + dashboardUrl + "\n\nTeam CrowdSpark";

        sendHtmlEmail(toEmail, "Update on your CrowdSpark campaign: \"" + projectTitle + "\"",
                "campaign-rejected", ctx, plainText);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Campaign funded
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Async("emailTaskExecutor")
    public void sendCampaignFundedEmail(String toEmail, String creatorName, String projectTitle, Long projectId,
                                        Double raisedAmount, Double goalAmount) {
        String safeName = nullSafe(creatorName, "there");
        double raised = raisedAmount == null ? 0.0 : raisedAmount;
        double goal   = goalAmount   == null ? 0.0 : goalAmount;
        int percent = goal > 0 ? (int) Math.round((raised / goal) * 100) : 100;
        String projectUrl = frontendUrl + "/projects/" + projectId;
        String raisedFormatted = formatInr(raised);
        String goalFormatted = formatInr(goal);

        Context ctx = baseContext();
        ctx.setVariable("creatorName", safeName);
        ctx.setVariable("projectTitle", projectTitle);
        ctx.setVariable("raisedFormatted", raisedFormatted);
        ctx.setVariable("goalFormatted", goalFormatted);
        ctx.setVariable("percent", percent);
        ctx.setVariable("projectUrl", projectUrl);
        ctx.setVariable("dashboardUrl", frontendUrl + "/dashboard/my-campaigns");

        String plainText = "Hi " + safeName + ",\n\n\"" + projectTitle + "\" is fully funded! You raised "
                + raisedFormatted + " of your " + goalFormatted + " goal (" + percent + "%).\n\n"
                + "Funds will be disbursed to your registered payout details shortly.\n\nTeam CrowdSpark";

        sendHtmlEmail(toEmail, "\"" + projectTitle + "\" is fully funded!", "campaign-funded", ctx, plainText);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Backer receipt (+ PDF attachment — Feature #10)
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @Async("emailTaskExecutor")
    public void sendBackerReceiptEmail(String toEmail, String backerName, String projectTitle, Long projectId,
                                       Long donationId, Double amount, String transactionId, String rewardTierTitle,
                                       LocalDateTime paidAt) {
        String safeName = nullSafe(backerName, "there");
        String safeTxnId = nullSafe(transactionId, "—");
        String amountFormatted = formatInr(amount == null ? 0.0 : amount);
        LocalDateTime safePaidAt = paidAt == null ? LocalDateTime.now() : paidAt;
        String paidAtFormatted = safePaidAt.format(RECEIPT_DATE_FORMAT);
        String projectUrl = frontendUrl + "/projects/" + projectId;

        Context ctx = baseContext();
        ctx.setVariable("backerName", safeName);
        ctx.setVariable("projectTitle", projectTitle);
        ctx.setVariable("amountFormatted", amountFormatted);
        ctx.setVariable("transactionId", safeTxnId);
        ctx.setVariable("rewardTierTitle", rewardTierTitle);
        ctx.setVariable("paidAtFormatted", paidAtFormatted);
        ctx.setVariable("projectUrl", projectUrl);
        ctx.setVariable("backedUrl", frontendUrl + "/dashboard/backed");

        String plainText = "Hi " + safeName + ",\n\nThanks for backing \"" + projectTitle
                + "\"! We've confirmed your contribution of " + amountFormatted + " on " + paidAtFormatted
                + " (transaction ID: " + safeTxnId + "). Your PDF receipt is attached.\n\nTeam CrowdSpark";

        // Feature #10: PDF receipt, attached to this same email. Generated here (not
        // in PaymentServiceImpl/DonationServiceImpl) so a PDF bug can never affect
        // the already-confirmed donation — worst case, this email just goes out
        // without its attachment.
        byte[] pdfBytes = null;
        try {
            pdfBytes = pdfReceiptService.generateReceiptPdf(
                    donationId, safeName, projectTitle, amount, safeTxnId, rewardTierTitle, safePaidAt);
        } catch (Exception e) {
            log.error("Receipt PDF generation failed for donation {} — sending email without attachment: {}",
                    donationId, e.getMessage(), e);
        }

        sendHtmlEmail(toEmail, "Your receipt for backing \"" + projectTitle + "\"", "backer-receipt", ctx, plainText,
                pdfBytes, "CrowdSpark-Receipt-" + donationId + ".pdf");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Context baseContext() {
        Context ctx = new Context();
        ctx.setVariable("frontendUrl", frontendUrl);
        ctx.setVariable("currentYear", Year.now().getValue());
        return ctx;
    }

    private void sendHtmlEmail(String to, String subject, String templateName, Context context, String plainTextFallback) {
        sendHtmlEmail(to, subject, templateName, context, plainTextFallback, null, null);
    }

    /** Feature #10 overload: attachmentBytes/attachmentFilename may both be null for no attachment. */
    private void sendHtmlEmail(String to, String subject, String templateName, Context context,
                               String plainTextFallback, byte[] attachmentBytes, String attachmentFilename) {
        try {
            String htmlContent = templateEngine.process("email/" + templateName, context);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(fromEmail, fromName);
            helper.setText(plainTextFallback, htmlContent);

            if (attachmentBytes != null && attachmentBytes.length > 0) {
                helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentBytes));
            }

            mailSender.send(mimeMessage);
            log.info("Sent '{}' email to {}{}", templateName, to, attachmentBytes != null ? " with attachment" : "");
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Rethrown unchecked so AsyncConfig's AsyncUncaughtExceptionHandler logs the
            // real SMTP/template error instead of it being silently swallowed.
            throw new IllegalStateException("Failed to send '" + templateName + "' email to " + to, e);
        }
    }

    private static String nullSafe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String formatInr(double amount) {
        NumberFormat nf = NumberFormat.getInstance(Locale.of("en", "IN"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "₹" + nf.format(amount);
    }
}