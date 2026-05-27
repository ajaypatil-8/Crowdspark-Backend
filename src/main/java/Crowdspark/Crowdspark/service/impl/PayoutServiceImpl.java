// src/main/java/Crowdspark/Crowdspark/service/impl/PayoutServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.PayoutResponse;
import Crowdspark.Crowdspark.entity.Payout;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PayoutStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.PayoutRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

    private final PayoutRepository   payoutRepository;
    private final ProjectRepository  projectRepository;
    private final UserRepository     userRepository;
    private final NotificationService notificationService;
    private final RestTemplate       restTemplate;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.account-number}")
    private String razorpayAccountNumber;

    @Value("${razorpay.payout.platform-fee-percent:5.0}")
    private Double platformFeePercent;

    private static final String RZP_BASE = "https://api.razorpay.com/v1";

    // ─────────────────────────────────────────────────────────────────────────
    // INITIATE PAYOUT
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PayoutResponse initiatePayout(Long projectId, Long adminId) {

        // 1. Load project — must be FUNDED
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (project.getStatus() != ProjectStatus.FUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payout can only be initiated for FUNDED projects. Current status: "
                    + project.getStatus());
        }

        // 2. Prevent duplicate payouts
        if (payoutRepository.existsByProject_Id(projectId)) {
            Payout existing = payoutRepository.findByProject_Id(projectId).get();
            if (existing.getStatus() == PayoutStatus.COMPLETED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Payout already completed for this project.");
            }
            if (existing.getStatus() == PayoutStatus.PROCESSING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Payout is already in progress. Razorpay payout id: "
                        + existing.getRazorpayPayoutId());
            }
        }

        // 3. Validate creator has a UPI ID
        User creator = project.getCreator();
        if (creator.getUpiId() == null || creator.getUpiId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Creator has not added a UPI ID. Ask them to update their profile.");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));

        // 4. Calculate amounts
        double gross          = project.getCurrentAmount();
        double feeAmount      = Math.round((gross * platformFeePercent / 100.0) * 100.0) / 100.0;
        double netAmount      = Math.round((gross - feeAmount) * 100.0) / 100.0;
        long   netInPaise     = Math.round(netAmount * 100);

        // 5. Create Payout record (INITIATED)
        Payout payout = new Payout();
        payout.setProject(project);
        payout.setCreator(creator);
        payout.setGrossAmount(gross);
        payout.setPlatformFeePercent(platformFeePercent);
        payout.setPlatformFeeAmount(feeAmount);
        payout.setNetAmount(netAmount);
        payout.setStatus(PayoutStatus.INITIATED);
        payout.setPayoutMode("UPI");
        payout.setUpiIdSnapshot(creator.getUpiId());
        payout.setInitiatedBy(admin);
        payout = payoutRepository.save(payout);

        // 6. Call Razorpay Payout API
        HttpHeaders headers = buildHeaders();
        try {
            // Step A: Create contact
            String contactId = createContact(creator, headers);
            payout.setRazorpayContactId(contactId);

            // Step B: Create fund account (UPI)
            String fundAccountId = createFundAccount(contactId, creator.getUpiId(), headers);
            payout.setRazorpayFundAccountId(fundAccountId);

            // Step C: Create payout
            String razorpayPayoutId = createRazorpayPayout(
                    fundAccountId, netInPaise, projectId, headers);
            payout.setRazorpayPayoutId(razorpayPayoutId);
            payout.setStatus(PayoutStatus.PROCESSING);

            log.info("Payout initiated: project={} creator={} net=₹{} razorpayPayoutId={}",
                    projectId, creator.getUsername(), netAmount, razorpayPayoutId);

        } catch (HttpClientErrorException e) {
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason("Razorpay error: " + e.getResponseBodyAsString());
            log.error("Razorpay payout failed for project {}: {}", projectId, e.getResponseBodyAsString());
        } catch (Exception e) {
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(e.getMessage());
            log.error("Payout failed for project {}: {}", projectId, e.getMessage(), e);
        }

        payout = payoutRepository.save(payout);

        // 7. Notify creator
        if (payout.getStatus() == PayoutStatus.PROCESSING) {
            notificationService.notifyCreatorPayoutInitiated(project, netAmount);
        } else {
            notificationService.notifyCreatorPayoutFailed(project, payout.getFailureReason());
        }

        return toResponse(payout);
    }

    @Override
    public List<PayoutResponse> getAllPayouts() {
        return payoutRepository.findAllByOrderByInitiatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    @Override
    public PayoutResponse getPayoutByProject(Long projectId) {
        Payout payout = payoutRepository.findByProject_Id(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No payout found for project " + projectId));
        return toResponse(payout);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Razorpay API helpers
    // ─────────────────────────────────────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBasicAuth(razorpayKeyId, razorpayKeySecret);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @SuppressWarnings("unchecked")
    private String createContact(User creator, HttpHeaders headers) {
        Map<String, Object> body = new HashMap<>();
        body.put("name",    creator.getName() != null ? creator.getName() : creator.getUsername());
        body.put("email",   creator.getEmail());
        body.put("contact", creator.getPhoneNumber() != null ? creator.getPhoneNumber() : "");
        body.put("type",    "vendor");

        ResponseEntity<Map> resp = restTemplate.exchange(
                RZP_BASE + "/contacts", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        return (String) resp.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String createFundAccount(String contactId, String upiId, HttpHeaders headers) {
        Map<String, Object> body = new HashMap<>();
        body.put("contact_id",   contactId);
        body.put("account_type", "vpa");
        body.put("vpa",          Map.of("address", upiId));

        ResponseEntity<Map> resp = restTemplate.exchange(
                RZP_BASE + "/fund_accounts", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        return (String) resp.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String createRazorpayPayout(String fundAccountId, long amountInPaise,
                                        Long projectId, HttpHeaders headers) {
        Map<String, Object> body = new HashMap<>();
        body.put("account_number",    razorpayAccountNumber);
        body.put("fund_account_id",   fundAccountId);
        body.put("amount",            amountInPaise);
        body.put("currency",          "INR");
        body.put("mode",              "UPI");
        body.put("purpose",           "payout");
        body.put("queue_if_low_balance", true);
        body.put("reference_id",      "cs_payout_proj_" + projectId + "_" + System.currentTimeMillis());
        body.put("narration",         "CrowdSpark Campaign Payout");

        ResponseEntity<Map> resp = restTemplate.exchange(
                RZP_BASE + "/payouts", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);

        return (String) resp.getBody().get("id");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapper
    // ─────────────────────────────────────────────────────────────────────────

    private PayoutResponse toResponse(Payout p) {
        return PayoutResponse.builder()
                .id(p.getId())
                .projectId(p.getProject().getId())
                .projectTitle(p.getProject().getTitle())
                .creatorId(p.getCreator().getId())
                .creatorUsername(p.getCreator().getUsername())
                .creatorUpiId(p.getUpiIdSnapshot())
                .grossAmount(p.getGrossAmount())
                .platformFeePercent(p.getPlatformFeePercent())
                .platformFeeAmount(p.getPlatformFeeAmount())
                .netAmount(p.getNetAmount())
                .status(p.getStatus().name())
                .payoutMode(p.getPayoutMode())
                .razorpayPayoutId(p.getRazorpayPayoutId())
                .failureReason(p.getFailureReason())
                .initiatedByUsername(p.getInitiatedBy() != null
                        ? p.getInitiatedBy().getUsername() : null)
                .initiatedAt(p.getInitiatedAt())
                .completedAt(p.getCompletedAt())
                .build();
    }
}
