package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.PaymentUpdateRequest;
import Crowdspark.Crowdspark.service.PaymentUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentUpdateController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentUpdateController.class);

    private final PaymentUpdateService paymentUpdateService;


    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @Valid @RequestBody PaymentUpdateRequest request
    ) {
        logger.debug("Received payment webhook: donationId={}, provider={}, status={}", request.getDonationId(),  request.getStatus());
        paymentUpdateService.processProviderUpdate(request, signature);
        return ResponseEntity.ok().build();
    }
}

