// src/main/java/Crowdspark/Crowdspark/service/impl/RefundServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.RefundResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.Refund;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.RefundRepository;
import Crowdspark.Crowdspark.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final DonationRepository donationRepository;
    private final RefundRepository   refundRepository;

    // AUDIT FIX (Feature #4, refund flow): the actual per-donation refund work
    // (and its @Transactional boundary) now lives in RefundTransactionExecutor,
    // a separate bean. Calling it here — from a *different* bean — genuinely
    // goes through Spring's transactional proxy, unlike the old in-class
    // `processSingleRefund(...)` self-call, which never did. See
    // RefundTransactionExecutor's class Javadoc for the full explanation.
    private final RefundTransactionExecutor refundTransactionExecutor;

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
                refundTransactionExecutor.processSingleRefund(donation, project);
            } catch (Exception e) {
                log.error("Refund failed for donation id={}: {}", donation.getId(), e.getMessage(), e);
            }
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
