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
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.PaymentService;
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

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

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
                    String.format("Amount exceeds remaining goal. Maximum you can contribute is ₹%.0f", remaining));
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
                        "Amount must be at least ₹" + rewardTier.getMinimumAmount() + " for this reward tier");
            }
        }

        // 4. Create Razorpay order (amount in paise = rupees × 100)
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
            log.info("Razorpay order created: {} for ₹{} by user {}", razorpayOrderId, request.getAmount(), backerId);
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
    // STEP 2 — Verify HMAC signature and confirm payment
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(value = {"projectDetails", "exploreFeed"}, allEntries = true)
    public DonationResponse verifyAndConfirm(PaymentVerifyRequest request, Long backerId) {

        // 1. Load the PENDING donation
        Donation donation = donationRepository.findById(request.getDonationId())
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

        // 5. Mark donation SUCCESS
        donation.setPaymentStatus(PaymentStatus.SUCCESS);
        donation.setTransactionId(request.getRazorpayPaymentId());
        donation.setPaidAt(LocalDateTime.now());
        donationRepository.save(donation);
        log.info("Payment confirmed for donation {} | paymentId: {}", donation.getId(), request.getRazorpayPaymentId());

        // 6. Update project.currentAmount
        Project project = donation.getProject();
        double newTotal = project.getCurrentAmount() + donation.getAmount();
        project.setCurrentAmount(newTotal);

        // Auto-close if goal reached
        if (newTotal >= project.getGoalAmount()) {
            project.setStatus(ProjectStatus.CLOSED);
            notificationService.notifyCreatorGoalReached(project);
        }
        projectRepository.save(project);

        // 7. Update backer stats
        User backer = donation.getBacker();
        backer.setTotalProjectsBacked(backer.getTotalProjectsBacked() + 1);
        backer.setTotalAmountBacked(backer.getTotalAmountBacked() + donation.getAmount());
        userRepository.save(backer);

        // 8. Update creator stats
        User creator = project.getCreator();
        creator.setTotalFundsRaised(creator.getTotalFundsRaised() + donation.getAmount());
        userRepository.save(creator);

        // 9. Fire notifications (async — non-blocking)
        notificationService.notifyCreatorBacked(project, backer, donation.getAmount());

        return toResponse(donation);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * HMAC-SHA256 verification.
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
