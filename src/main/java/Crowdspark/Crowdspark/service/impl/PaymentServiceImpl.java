package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.PaymentResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Payment;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.UserReward;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.PaymentRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardRepository;
import Crowdspark.Crowdspark.repository.UserRewardRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.PaymentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final DonationRepository donationRepository;
    private final ProjectRepository projectRepository;   // ✅ FIX
    private final RewardRepository rewardRepository;
    private final UserRewardRepository userRewardRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public PaymentResponse markPaymentStatus(Long donationId, PaymentStatus status) {

        Payment payment = paymentRepository.findByDonationId(donationId)
                .orElseThrow(() -> new AuthException("Payment not found"));

        // ✅ Idempotency safety (optional but recommended)
        if (payment.getStatus() == PaymentStatus.SUCCESS ||
                payment.getStatus() == PaymentStatus.FAILED) {
            logger.info("Payment already final, skipping update");
            return mapResponse(payment);
        }

        payment.setStatus(status);
        Payment saved = paymentRepository.save(payment);

        // ✅ ADD AMOUNT ONLY AFTER PAYMENT SUCCESS
        if (status == PaymentStatus.SUCCESS) {

            Donation donation = donationRepository.findById(donationId)
                    .orElseThrow(() -> new AuthException("Donation not found"));

            Project project = projectRepository.findById(donation.getProjectId())
                    .orElseThrow(() -> new AuthException("Project not found"));

            project.setCurrentAmount(
                    project.getCurrentAmount() + donation.getAmount()
            );

            projectRepository.save(project);

            // 🎁 Assign reward
            rewardRepository.findByProjectIdOrderByMinAmountDesc(donation.getProjectId())
                    .stream()
                    .filter(r -> donation.getAmount() >= r.getMinAmount())
                    .findFirst()
                    .ifPresent(reward -> {
                        UserReward ur = new UserReward();
                        ur.setUserId(donation.getUserId());
                        ur.setRewardId(reward.getId());
                        ur.setDonationId(donationId);
                        userRewardRepository.save(ur);
                    });
        }

        auditLogService.log(
                null,
                "SYSTEM_PAYMENT_UPDATE",
                "PAYMENT",
                saved.getId()
        );

        logger.info(
                "Payment status updated: paymentId={}, status={}",
                saved.getId(),
                saved.getStatus()
        );

        return mapResponse(saved);
    }

    private PaymentResponse mapResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .donationId(payment.getDonationId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .provider(payment.getProvider())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
