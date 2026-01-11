package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.PaymentUpdateRequest;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.PaymentRepository;
import Crowdspark.Crowdspark.service.PaymentService;
import Crowdspark.Crowdspark.service.PaymentUpdateService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class PaymentUpdateServiceImpl implements PaymentUpdateService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentUpdateServiceImpl.class);

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

    @Value("${payments.webhook.secret:}")
    private String webhookSecret;

    @Override
    @Transactional
    public void processProviderUpdate(PaymentUpdateRequest request, String signatureHeader) {

        // 1️⃣ Verify signature (optional but safe)
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (signatureHeader == null || !verifySignature(request, signatureHeader)) {
                logger.warn("Invalid webhook signature for donationId={}", request.getDonationId());
                throw new AuthException("Invalid signature");
            }
        }

        // 2️⃣ Fetch payment
        var payment = paymentRepository.findByDonationId(request.getDonationId())
                .orElseThrow(() -> new AuthException("Payment not found"));

        // 3️⃣ Idempotency check
        if (isFinal(payment.getStatus())) {
            logger.info(
                    "Webhook ignored: paymentId={} already in final state {}",
                    payment.getId(),
                    payment.getStatus()
            );
            return;
        }

        // 4️⃣ Delegate to domain service
        paymentService.markPaymentStatus(
                request.getDonationId(),
                request.getStatus()
        );

        logger.info(
                "Processed payment webhook: donationId={} status={}",
                request.getDonationId(),
                request.getStatus()
        );
    }

    private boolean isFinal(PaymentStatus status) {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED;
    }

    private boolean verifySignature(PaymentUpdateRequest request, String signatureHeader) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String payload = request.getDonationId() + "|" + request.getStatus();
            byte[] digest = md.digest((payload + webhookSecret.trim()).getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest).equalsIgnoreCase(signatureHeader);
        } catch (Exception e) {
            logger.error("Signature verification failed", e);
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
