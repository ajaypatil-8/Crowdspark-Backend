package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.PaymentUpdateRequest;

public interface PaymentUpdateService {


    void processProviderUpdate(PaymentUpdateRequest request, String signatureHeader);
}

