// src/main/java/Crowdspark/Crowdspark/service/impl/PaymentServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.dto.PaymentOrderRequest;
import Crowdspark.Crowdspark.dto.PaymentOrderResponse;
import Crowdspark.Crowdspark.dto.PaymentVerifyRequest;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.RewardTier;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.metrics.PlatformMetrics;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.EmailService;
import Crowdspark.Crowdspark.service.FundingStreamService;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.PaymentService;
import Crowdspark.Crowdspark.service.PdfReceiptService;
import Crowdspark.Crowdspark.service.RewardClaimService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final DonationRepository    donationRepository;
    private final ProjectRepository     projectRepository;
    private final UserRepository        userRepository;
    private final RewardTierRepository  rewardTierRepository;
    private final NotificationService   notificationService;
    private final FundingStreamService fundingStreamService;
    private final RewardClaimService rewardClaimService;
    private final EmailService emailService; // <- Feature #9
    private final PdfReceiptService pdfReceiptService; // <- FIX #10: on-demand download
    private final PlatformMetrics platformMetrics; // <- Feature #31

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    // AUDIT FIX (Feature #4): Razorpay's own signed server-to-server callback
    // secret. Completely separate from razorpay.key-secret above -- that one
    // signs the (orderId, paymentId) pair the *browser* gets back from
    // checkout.js; this one signs the *webhook payload* Razorpay's servers
    // send directly to us. Configure this in your Razorpay dashboard under
    // Settings -> Webhooks when you register the webhook URL.
    @Value("${razorpay.webhook-secret:}")
    private String razorpayWebhookSecret;

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 1 — Create Razorpay order + PENDING donation
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PaymentOrderResponse createOrder(PaymentOrderRequest request, Long backerId) {

        // 1. Load backer
        User backer = userRepository.findById(backerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 2. Load and validate project
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (project.getCreator().getId().equals(backerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot back your own campaign");
        }
        if (project.getStatus() != ProjectStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project is not accepting donations");
        }
        if (project.getDeadline().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project funding deadline has passed");
        }

        double remaining = project.getGoalAmount() - project.getCurrentAmount();
        if (remaining <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This project has already reached its funding goal");
        }
        if (request.getAmount() > remaining) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Amount exceeds remaining goal. Maximum you can contribute is \u20b9%.0f", remaining));
        }

        // 3. Validate optional reward tier
        RewardTier rewardTier = null;
        if (request.getRewardTierId() != null) {
            rewardTier = rewardTierRepository.findById(request.getRewardTierId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reward tier not found"));
            if (!rewardTier.getProject().getId().equals(project.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reward tier does not belong to this project");
            }
            if (request.getAmount() < rewardTier.getMinimumAmount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Amount must be at least \u20b9" + rewardTier.getMinimumAmount() + " for this reward tier");
            }
            // BUG FIX (Feature #24): limitedQuantity/quantityAvailable were
            // captured on the tier but never checked anywhere -- a "limited
            // to N" reward could be selected and paid for by unlimited
            // backers. This is the primary check (before payment even
            // starts); the atomic decrement in RewardClaimServiceImpl is the
            // second line of defense for the rare case of two people
            // checking out the last unit at nearly the same instant.
            if (rewardTier.getLimitedQuantity() != null
                    && rewardTier.getQuantityAvailable() != null
                    && rewardTier.getQuantityAvailable() <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This reward tier is sold out. Please choose another tier or continue without one.");
            }
        }

        // 4. Create Razorpay order (amount in paise = rupees x 100)
        long amountInPaise = Math.round(request.getAmount() * 100);
        String razorpayOrderId;
        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount",   amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt",  "cs_proj_" + project.getId() + "_user_" + backerId);
            orderRequest.put("payment_capture", true); // auto-capture
            Order order = client.orders.create(orderRequest);
            razorpayOrderId = order.get("id");
            log.info("Razorpay order created: {} for \u20b9{} by user {}", razorpayOrderId, request.getAmount(), backerId);
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment gateway error. Please try again.");
        }

        // 5. Save PENDING donation (no amounts updated yet — only on verification)
        Donation donation = new Donation();
        donation.setBacker(backer);
        donation.setProject(project);
        donation.setAmount(request.getAmount());
        donation.setRewardTier(rewardTier);
        donation.setPaymentStatus(PaymentStatus.PENDING);
        donation.setRazorpayOrderId(razorpayOrderId);
        donation.setMessage(request.getMessage());
        Donation saved = donationRepository.save(donation);

        return PaymentOrderResponse.builder()
                .razorpayOrderId(razorpayOrderId)
                .amountInPaise(amountInPaise)
                .currency("INR")
                .razorpayKeyId(razorpayKeyId)
                .donationId(saved.getId())
                .projectTitle(project.getTitle())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 2 — Verify HMAC signature (client-driven) and confirm payment
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(value = {"projectDetails", "exploreFeed"}, allEntries = true)
    public DonationResponse verifyAndConfirm(PaymentVerifyRequest request, Long backerId) {

        // 1. Load the PENDING donation
        // BUG FIX (Feature #1/#4): locked read -- see DonationRepository.findByIdForUpdate
        // for why a plain findById() here raced with the webhook path below.
        Donation donation = donationRepository.findByIdForUpdate(request.getDonationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation not found"));

        // 2. Security: ensure this donation belongs to the caller
        if (!donation.getBacker().getId().equals(backerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your donation");
        }

        // 3. Prevent double-processing
        if (donation.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.warn("Duplicate verify attempt for donation {}", donation.getId());
            return toResponse(donation);
        }

        // AUDIT FIX (Feature #1/#2): this request's orderId must be the SAME
        // order we created for THIS donation. Without this check, a caller who
        // legitimately paid a small amount (and so holds one genuinely valid
        // (orderId, paymentId, signature) triple from Razorpay) could resubmit
        // that same valid triple against a different, larger unpaid donation ID
        // they own -- the HMAC check below would pass, because the triple *is*
        // genuinely valid, just for a different order than this donation.
        if (donation.getRazorpayOrderId() == null
                || !donation.getRazorpayOrderId().equals(request.getRazorpayOrderId())) {
            log.warn("Order ID mismatch for donation {}: expected {} but request had {}",
                    donation.getId(), donation.getRazorpayOrderId(), request.getRazorpayOrderId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This payment does not match the order created for this donation.");
        }

        // 4. Verify HMAC-SHA256 signature
        //    Razorpay spec: HMAC_SHA256(orderId + "|" + paymentId, keySecret)
        if (!verifySignature(request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature())) {
            donation.setPaymentStatus(PaymentStatus.FAILED);
            donationRepository.save(donation);
            log.warn("Razorpay signature verification FAILED for donation {}", donation.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment verification failed. Signature mismatch.");
        }

        return confirmDonationPaid(donation, request.getRazorpayPaymentId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUDIT FIX (Feature #4) — Razorpay webhook (server-to-server confirmation)
    //
    // The client-driven verify() above depends on the browser staying open
    // long enough to call /verify after Razorpay charges the card. If the tab
    // closes or the network drops right after a successful charge, the
    // donation is stuck PENDING forever even though Razorpay actually took the
    // money. This endpoint is Razorpay's own server telling us directly,
    // independent of whatever the browser does, so that case gets reconciled
    // too. Register this URL (https://yourdomain.com/crowdspark/api/v1/payment/webhook)
    // in the Razorpay dashboard under Settings -> Webhooks, subscribed to the
    // "payment.captured" event, and put the webhook secret it gives you in
    // razorpay.webhook-secret.
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(value = {"projectDetails", "exploreFeed"}, allEntries = true)
    public void confirmFromWebhook(String rawBody, String webhookSignature) {

        if (razorpayWebhookSecret == null || razorpayWebhookSecret.isBlank()) {
            log.error("Razorpay webhook received but razorpay.webhook-secret is not configured — rejecting.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook not configured");
        }

        if (!verifyWebhookSignature(rawBody, webhookSignature)) {
            log.warn("Razorpay webhook signature verification FAILED");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
        }

        JSONObject payload = new JSONObject(rawBody);
        String event = payload.optString("event", "");

        // Only "payment.captured" actually means money has landed. Every other
        // event type (payment.failed, refund.processed, order.paid, etc.) is
        // acknowledged with 200 OK by the controller but not acted on here.
        if (!"payment.captured".equals(event)) {
            log.info("Razorpay webhook: ignoring event type '{}'", event);
            return;
        }

        JSONObject paymentEntity = payload
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String razorpayOrderId   = paymentEntity.optString("order_id", null);
        String razorpayPaymentId = paymentEntity.optString("id", null);

        if (razorpayOrderId == null || razorpayPaymentId == null) {
            log.warn("Razorpay webhook payload missing order_id/payment id: {}", rawBody);
            return;
        }

        // BUG FIX (Feature #1/#4): locked read -- see DonationRepository.findByIdForUpdate.
        Donation donation = donationRepository.findByRazorpayOrderIdForUpdate(razorpayOrderId).orElse(null);
        if (donation == null) {
            // Could be an order from a different environment/account, or one we
            // never got as far as saving. Nothing to reconcile, and nothing
            // sensitive to leak back to the caller either way.
            log.warn("Razorpay webhook: no donation found for order {}", razorpayOrderId);
            return;
        }

        if (donation.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.info("Razorpay webhook: donation {} already SUCCESS, ignoring duplicate webhook", donation.getId());
            return;
        }

        confirmDonationPaid(donation, razorpayPaymentId);
        log.info("Donation {} confirmed via Razorpay webhook (order {})", donation.getId(), razorpayOrderId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared "this donation is genuinely paid" logic, used by both
    // verifyAndConfirm() (client-driven) and confirmFromWebhook() (server-driven)
    // so the two paths can never drift out of sync with each other.
    // ─────────────────────────────────────────────────────────────────────────

    private DonationResponse confirmDonationPaid(Donation donation, String razorpayPaymentId) {

        // Mark donation SUCCESS
        donation.setPaymentStatus(PaymentStatus.SUCCESS);
        rewardClaimService.createClaimForDonation(donation);
        donation.setTransactionId(razorpayPaymentId);
        donation.setPaidAt(LocalDateTime.now());
        donationRepository.save(donation);
        log.info("Payment confirmed for donation {} | paymentId: {}", donation.getId(), razorpayPaymentId);
        platformMetrics.recordSuccessfulDonation(donation.getAmount());

        // Update project.currentAmount
        Project project = donation.getProject();
        double newTotal = project.getCurrentAmount() + donation.getAmount();
        project.setCurrentAmount(newTotal);

        // AUDIT FIX (Feature #2/#3): this used to set ProjectStatus.CLOSED when
        // the goal was reached before the deadline. CLOSED was never a status
        // the deadline scheduler (which only looks at APPROVED projects) or
        // PayoutServiceImpl (which strictly requires FUNDED) would ever pick up
        // again -- so a campaign that succeeded early got stuck forever with no
        // path to ever being paid out to its creator. FUNDED is the status that
        // actually means "successfully funded, eligible for payout", so a
        // campaign that hits its goal goes straight there instead of waiting on
        // the deadline scheduler (which still handles the "still APPROVED when
        // the deadline passes" case for campaigns that reach FUNDED exactly at
        // the deadline via the scheduler, or FAILED if they never hit goal).
        if (newTotal >= project.getGoalAmount()) {
            project.setStatus(ProjectStatus.FUNDED);
            notificationService.notifyCreatorGoalReached(project);
        }
        projectRepository.save(project);

        // FIX #15: this used to broadcast immediately, mid-transaction. If
        // anything below (backer/creator stat updates, notifications) had
        // thrown and rolled this transaction back, every connected viewer
        // would already have been pushed a funding amount that the DB never
        // actually committed to. Deferring to afterCommit — and rebuilding
        // the snapshot fresh at that point rather than reusing `project` —
        // guarantees viewers only ever see amounts that are actually real,
        // and reflect any other donation that committed in the meantime too.
        Long broadcastProjectId = project.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fundingStreamService.broadcast(
                            broadcastProjectId,
                            fundingStreamService.buildSnapshot(broadcastProjectId)
                    );
                }
            });
        } else {
            // No transaction in progress (e.g. called directly in a unit test) —
            // fire immediately rather than silently dropping the broadcast.
            fundingStreamService.broadcast(
                    broadcastProjectId,
                    fundingStreamService.buildSnapshot(broadcastProjectId)
            );
        }

        // Update backer stats
        User backer = donation.getBacker();
        backer.setTotalProjectsBacked(backer.getTotalProjectsBacked() + 1);
        backer.setTotalAmountBacked(backer.getTotalAmountBacked() + donation.getAmount());
        userRepository.save(backer);

        // Update creator stats
        User creator = project.getCreator();
        creator.setTotalFundsRaised(creator.getTotalFundsRaised() + donation.getAmount());
        userRepository.save(creator);

        // Fire notifications (async — non-blocking)
        notificationService.notifyCreatorBacked(project, backer, donation.getAmount());

        // Feature #9/#10: HTML backer-receipt email with PDF receipt attached
        //     (PDF is generated inside EmailServiceImpl via PdfReceiptService, so a
        //     PDF bug can never affect this already-confirmed payment).
        String rewardTierTitle = donation.getRewardTier() != null ? donation.getRewardTier().getTitle() : null;
        emailService.sendBackerReceiptEmail(
                backer.getEmail(),
                backer.getName(),
                project.getTitle(),
                project.getId(),
                donation.getId(),
                donation.getAmount(),
                donation.getTransactionId(),
                rewardTierTitle,
                donation.getPaidAt()
        );

        return toResponse(donation);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIX #10 — on-demand receipt download (this didn't exist at all before)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public byte[] getReceiptPdf(Long donationId, Long requesterId) {

        Donation donation = donationRepository.findDetailedById(donationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation not found"));

        boolean isOwner = donation.getBacker().getId().equals(requesterId);
        if (!isOwner) {
            User requester = userRepository.findById(requesterId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            if (!requester.hasRole(Role.ADMIN)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You do not have permission to view this receipt");
            }
        }

        if (donation.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A receipt is only available for successful payments");
        }

        String rewardTierTitle = donation.getRewardTier() != null ? donation.getRewardTier().getTitle() : null;

        return pdfReceiptService.generateReceiptPdf(
                donation.getId(),
                donation.getBacker().getName(),
                donation.getProject().getTitle(),
                donation.getAmount(),
                donation.getTransactionId(),
                rewardTierTitle,
                donation.getPaidAt()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * HMAC-SHA256 verification for the client checkout flow.
     * Razorpay spec: signature = HMAC_SHA256(orderId + "|" + paymentId, keySecret)
     */
    private boolean verifySignature(String orderId, String paymentId, String receivedSignature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String generated = new String(Hex.encodeHex(hash));
            return generated.equals(receivedSignature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * HMAC-SHA256 verification for the Razorpay webhook.
     * Razorpay spec: signature = HMAC_SHA256(rawRequestBody, webhookSecret)
     * Note this is a DIFFERENT secret and a DIFFERENT signed payload than
     * verifySignature() above — do not mix the two up.
     */
    private boolean verifyWebhookSignature(String rawBody, String receivedSignature) {
        if (receivedSignature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String generated = new String(Hex.encodeHex(hash));
            return generated.equals(receivedSignature);
        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private DonationResponse toResponse(Donation d) {
        String thumbnail = d.getProject().getMedia().stream()
                .filter(m -> m.getUsage() == MediaUsage.THUMBNAIL)
                .map(m -> m.getMediaUrl())
                .findFirst().orElse(null);

        return DonationResponse.builder()
                .id(d.getId())
                .projectId(d.getProject().getId())
                .projectTitle(d.getProject().getTitle())
                .projectThumbnailUrl(thumbnail)
                .backerId(d.getBacker().getId())
                .backerUsername(d.getBacker().getUsername())
                .amount(d.getAmount())
                .paymentStatus(d.getPaymentStatus().name())
                .transactionId(d.getTransactionId())
                .message(d.getMessage())
                .rewardTierId(d.getRewardTier() != null ? d.getRewardTier().getId() : null)
                .rewardTierTitle(d.getRewardTier() != null ? d.getRewardTier().getTitle() : null)
                .createdAt(d.getCreatedAt())
                .paidAt(d.getPaidAt())
                .build();
    }
}
