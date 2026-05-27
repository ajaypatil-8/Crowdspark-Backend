// src/main/java/Crowdspark/Crowdspark/service/impl/RefundServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.RefundResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.Refund;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.RefundStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.RefundRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final DonationRepository  donationRepository;
    private final RefundRepository    refundRepository;
    private final UserRepository      userRepository;
    private final NotificationService notificationService;
    private final RestTemplate        restTemplate;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    private static final String RZP_BASE = "https://api.razorpay.com/v1";

    // ─────────────────────────────────────────────────────────────────────────
    // Called by DeadlineSchedulerService when project → FAILED
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void processRefundsForProject(Project project) {
        List<Donation> donations = donationRepository
                .findByProject_IdAndPaymentStatus(project.getId(), PaymentStatus.SUCCESS);

        if (donations.isEmpty()) {
            log.info("No donations to refund for project id={}", project.getId());
            return;
        }

        log.info("Processing {} refund(s) for failed project id={} \"{}\"",
                donations.size(), project.getId(), project.getTitle());

        for (Donation donation : donations) {
            try {
                processSingleRefund(donation, project);
            } catch (Exception e) {
                log.error("Refund failed for donation id={}: {}", donation.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    protected void processSingleRefund(Donation donation, Project project) {

        // Skip already refunded donations
        if (donation.getRefundStatus() == RefundStatus.COMPLETED) {
            log.debug("Donation id={} already refunded — skipping", donation.getId());
            return;
        }

        // Skip donations with no Razorpay payment ID (shouldn't happen, but safety check)
        if (donation.getTransactionId() == null || donation.getTransactionId().isBlank()) {
            log.warn("Donation id={} has no transactionId — cannot refund", donation.getId());
            return;
        }

        // Create Refund record (INITIATED)
        Refund refund = new Refund();
        refund.setDonation(donation);
        refund.setBacker(donation.getBacker());
        refund.setProject(project);
        refund.setAmount(donation.getAmount());
        refund.setStatus(RefundStatus.INITIATED);
        refund = refundRepository.save(refund);

        // Call Razorpay Refund API
        try {
            String razorpayRefundId = callRazorpayRefund(
                    donation.getTransactionId(),
                    donation.getAmount(),
                    donation.getId()
            );

            // Mark refund completed
            refund.setRazorpayRefundId(razorpayRefundId);
            refund.setStatus(RefundStatus.COMPLETED);
            refund.setCompletedAt(LocalDateTime.now());
            refundRepository.save(refund);

            // Update donation
            donation.setPaymentStatus(PaymentStatus.REFUNDED);
            donation.setRefundStatus(RefundStatus.COMPLETED);
            donation.setRazorpayRefundId(razorpayRefundId);
            donation.setRefundedAt(LocalDateTime.now());
            donationRepository.save(donation);

            // Reverse backer stats
            User backer = donation.getBacker();
            backer.setTotalAmountBacked(
                    Math.max(0, backer.getTotalAmountBacked() - donation.getAmount()));
            backer.setTotalProjectsBacked(
                    Math.max(0, backer.getTotalProjectsBacked() - 1));
            userRepository.save(backer);

            // Notify backer
            notificationService.notifyBackerRefundProcessed(
                    donation.getBacker(), project, donation.getAmount());

            log.info("Refund COMPLETED: donation={} razorpayRefundId={} amount=₹{}",
                    donation.getId(), razorpayRefundId, donation.getAmount());

        } catch (HttpClientErrorException e) {
            String reason = "Razorpay error: " + e.getResponseBodyAsString();
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason(reason);
            refundRepository.save(refund);

            donation.setRefundStatus(RefundStatus.FAILED);
            donationRepository.save(donation);

            notificationService.notifyBackerRefundFailed(
                    donation.getBacker(), project, donation.getAmount(), reason);

            log.error("Refund FAILED for donation={}: {}", donation.getId(), reason);

        } catch (Exception e) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason(e.getMessage());
            refundRepository.save(refund);

            donation.setRefundStatus(RefundStatus.FAILED);
            donationRepository.save(donation);

            log.error("Refund FAILED for donation={}: {}", donation.getId(), e.getMessage(), e);
        }
    }

    @Override
    public List<RefundResponse> getRefundsForProject(Long projectId) {
        return refundRepository.findByProject_IdOrderByInitiatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<RefundResponse> getRefundsForBacker(Long backerId) {
        return refundRepository.findByBacker_IdOrderByInitiatedAtDesc(backerId)
                .stream().map(this::toResponse).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Razorpay Refund API
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String callRazorpayRefund(String paymentId, Double amount, Long donationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(razorpayKeyId, razorpayKeySecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // amount is optional for full refund — include it for precision
        long amountInPaise = Math.round(amount * 100);
        Map<String, Object> body = new HashMap<>();
        body.put("amount",  amountInPaise);
        body.put("speed",   "optimum");   // instant if possible, else normal 3-5 days
        body.put("receipt", "cs_refund_donation_" + donationId);
        body.put("notes",   Map.of("reason", "Campaign did not reach its funding goal"));

        ResponseEntity<Map> resp = restTemplate.exchange(
                RZP_BASE + "/payments/" + paymentId + "/refund",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        return (String) resp.getBody().get("id");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapper
    // ─────────────────────────────────────────────────────────────────────────

    private RefundResponse toResponse(Refund r) {
        return RefundResponse.builder()
                .id(r.getId())
                .donationId(r.getDonation().getId())
                .projectId(r.getProject().getId())
                .projectTitle(r.getProject().getTitle())
                .backerId(r.getBacker().getId())
                .backerUsername(r.getBacker().getUsername())
                .amount(r.getAmount())
                .status(r.getStatus().name())
                .razorpayRefundId(r.getRazorpayRefundId())
                .failureReason(r.getFailureReason())
                .initiatedAt(r.getInitiatedAt())
                .completedAt(r.getCompletedAt())
                .build();
    }


}
