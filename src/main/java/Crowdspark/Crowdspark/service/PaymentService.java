package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.PaymentResponse;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;

public interface PaymentService {

    PaymentResponse markPaymentStatus(Long donationId, PaymentStatus status);

}
