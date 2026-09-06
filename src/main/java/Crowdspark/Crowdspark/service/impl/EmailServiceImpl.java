package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.WeeklyInsightItem;
import Crowdspark.Crowdspark.queue.RedisQueueService;
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
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private static final String EMAIL_QUEUE = "email";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final PdfReceiptService pdfReceiptService; // ← Feature #10
    private final RedisQueueService queueService;       // ← Feature #36

    public EmailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine,
                            PdfReceiptService pdfReceiptService, RedisQueueService queueService) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.pdfReceiptService = pdfReceiptService;
        this.queueService = queueService;
    }


    @Value("${app.mail.from-address}")
    private String fromEmail;

    @Value("${app.mail.from-name:CrowdSpark}")
    private String fromName;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;


    private static final DateTimeFormatter RECEIPT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a", Locale.ENGLISH);


    public record OtpEmailPayload(String toEmail, String name, String otp, int validityMinutes) {}
    public record SimpleEmailPayload(String toEmail, String subject, String body) {}
    public record WelcomeEmailPayload(String toEmail, String name) {}
    public record CampaignApprovedPayload(String toEmail, String creatorName, String projectTitle, Long projectId) {}
    public record CampaignRejectedPayload(String toEmail, String creatorName, String projectTitle, String reason) {}
    public record CampaignFundedPayload(String toEmail, String creatorName, String projectTitle, Long projectId,
                                        Double raisedAmount, Double goalAmount) {}
    public record BackerReceiptPayload(String toEmail, String backerName, String projectTitle, Long projectId,
                                       Long donationId, Double amount, String transactionId,
                                       String rewardTierTitle, LocalDateTime paidAt) {}
    public record WeeklyInsightDigestPayload(String toEmail, String creatorName, List<WeeklyInsightItem> items) {}

    // ─────────────────────────────────────────────────────────────────────────
    // OTP — numeric code (creator upgrade / KYC re-verification)
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void sendOtpEmail(String toEmail, String name, String otp, int validityMinutes) {
        queueService.enqueue(EMAIL_QUEUE, "OTP", new OtpEmailPayload(toEmail, name, otp, validityMinutes),
                () -> sendOtpEmailNow(toEmail, name, otp, validityMinutes));
    }

    public void sendOtpEmailNow(String toEmail, String name, String otp, int validityMinutes) {
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
    public void sendSimpleEmail(String toEmail, String subject, String body) {
        queueService.enqueue(EMAIL_QUEUE, "SIMPLE", new SimpleEmailPayload(toEmail, subject, body),
                () -> sendSimpleEmailNow(toEmail, subject, body));
    }

    public void sendSimpleEmailNow(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        message.setFrom(fromEmail);
        mailSender.send(message);

        log.info("Sent '{}' email to {}", subject, toEmail);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Welcome — sent once, right after registration
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void sendWelcomeEmail(String toEmail, String name) {
        queueService.enqueue(EMAIL_QUEUE, "WELCOME", new WelcomeEmailPayload(toEmail, name),
                () -> sendWelcomeEmailNow(toEmail, name));
    }

    public void sendWelcomeEmailNow(String toEmail, String name) {
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
    public void sendCampaignApprovedEmail(String toEmail, String creatorName, String projectTitle, Long projectId) {
        queueService.enqueue(EMAIL_QUEUE, "CAMPAIGN_APPROVED",
                new CampaignApprovedPayload(toEmail, creatorName, projectTitle, projectId),
                () -> sendCampaignApprovedEmailNow(toEmail, creatorName, projectTitle, projectId));
    }

    public void sendCampaignApprovedEmailNow(String toEmail, String creatorName, String projectTitle, Long projectId) {
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
    public void sendCampaignRejectedEmail(String toEmail, String creatorName, String projectTitle, String reason) {
        queueService.enqueue(EMAIL_QUEUE, "CAMPAIGN_REJECTED",
                new CampaignRejectedPayload(toEmail, creatorName, projectTitle, reason),
                () -> sendCampaignRejectedEmailNow(toEmail, creatorName, projectTitle, reason));
    }

    public void sendCampaignRejectedEmailNow(String toEmail, String creatorName, String projectTitle, String reason) {
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
    public void sendCampaignFundedEmail(String toEmail, String creatorName, String projectTitle, Long projectId,
                                        Double raisedAmount, Double goalAmount) {
        queueService.enqueue(EMAIL_QUEUE, "CAMPAIGN_FUNDED",
                new CampaignFundedPayload(toEmail, creatorName, projectTitle, projectId, raisedAmount, goalAmount),
                () -> sendCampaignFundedEmailNow(toEmail, creatorName, projectTitle, projectId, raisedAmount, goalAmount));
    }

    public void sendCampaignFundedEmailNow(String toEmail, String creatorName, String projectTitle, Long projectId,
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
    public void sendBackerReceiptEmail(String toEmail, String backerName, String projectTitle, Long projectId,
                                       Long donationId, Double amount, String transactionId, String rewardTierTitle,
                                       LocalDateTime paidAt) {
        queueService.enqueue(EMAIL_QUEUE, "BACKER_RECEIPT",
                new BackerReceiptPayload(toEmail, backerName, projectTitle, projectId, donationId, amount,
                        transactionId, rewardTierTitle, paidAt),
                () -> sendBackerReceiptEmailNow(toEmail, backerName, projectTitle, projectId, donationId, amount,
                        transactionId, rewardTierTitle, paidAt));
    }

    public void sendBackerReceiptEmailNow(String toEmail, String backerName, String projectTitle, Long projectId,
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
    // Weekly insight digest (Feature #48) — one email per creator, listing
    // every live campaign's AI-written weekly summary together, rather than
    // a separate email per campaign.
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void sendWeeklyInsightDigestEmail(String toEmail, String creatorName, List<WeeklyInsightItem> items) {
        queueService.enqueue(EMAIL_QUEUE, "WEEKLY_INSIGHT_DIGEST",
                new WeeklyInsightDigestPayload(toEmail, creatorName, items),
                () -> sendWeeklyInsightDigestEmailNow(toEmail, creatorName, items));
    }

    public void sendWeeklyInsightDigestEmailNow(String toEmail, String creatorName, List<WeeklyInsightItem> items) {
        if (items == null || items.isEmpty()) {
            log.warn("Weekly insight digest requested for {} with no items — skipping send", toEmail);
            return;
        }
        String safeName = nullSafe(creatorName, "there");

        // Mapped to plain Maps (not the WeeklyInsightItem records directly)
        // specifically for Thymeleaf: ${item.projectTitle} against a Map
        // resolves unambiguously via get("projectTitle"), whereas relying on
        // SpEL to resolve a record's no-prefix accessor method is a detail
        // of the exact Spring version that isn't worth risking in a template
        // that only gets visually checked once it's already emailing people.
        List<java.util.Map<String, Object>> templateItems = new java.util.ArrayList<>();
        for (WeeklyInsightItem item : items) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("projectTitle", item.projectTitle());
            m.put("projectId", item.projectId());
            m.put("summary", item.summary());
            m.put("fundedPercent", Math.max(0, Math.min(100, item.fundedPercent())));
            m.put("newBackersThisWeek", item.newBackersThisWeek());
            m.put("projectUrl", frontendUrl + "/projects/" + item.projectId());
            templateItems.add(m);
        }

        Context ctx = baseContext();
        ctx.setVariable("creatorName", safeName);
        ctx.setVariable("items", templateItems);
        ctx.setVariable("dashboardUrl", frontendUrl + "/dashboard");
        ctx.setVariable("campaignCount", items.size());

        StringBuilder plain = new StringBuilder("Hi ").append(safeName)
                .append(", here's how your campaign").append(items.size() > 1 ? "s" : "")
                .append(" did this week:\n\n");
        for (WeeklyInsightItem item : items) {
            plain.append("\"").append(item.projectTitle()).append("\" (").append(item.fundedPercent())
                    .append("% funded): ").append(item.summary()).append("\n\n");
        }
        plain.append("Team CrowdSpark");

        String subject = items.size() == 1
                ? "This week on \"" + items.get(0).projectTitle() + "\""
                : "Your weekly CrowdSpark update — " + items.size() + " campaigns";

        sendHtmlEmail(toEmail, subject, "weekly-insight-digest", ctx, plain.toString());
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