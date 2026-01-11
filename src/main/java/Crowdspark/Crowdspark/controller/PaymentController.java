package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.PaymentResponse;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    @PreAuthorize("hasRole('BACKER')")
    @PostMapping("/{donationId}/success")
    public ResponseEntity<PaymentResponse> markSuccess(@PathVariable Long donationId) {
        PaymentResponse response = paymentService.markPaymentStatus(donationId, PaymentStatus.SUCCESS);

        logger.info("Payment marked success for donationId={}; paymentId={}", donationId, response.getId());

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('BACKER')")
    @PostMapping("/{donationId}/fail")
    public ResponseEntity<PaymentResponse> markFailed(@PathVariable Long donationId) {
        PaymentResponse response = paymentService.markPaymentStatus(donationId, PaymentStatus.FAILED);

        logger.info("Payment marked failed for donationId={}; paymentId={}", donationId, response.getId());

        return ResponseEntity.ok(response);
    }
}
