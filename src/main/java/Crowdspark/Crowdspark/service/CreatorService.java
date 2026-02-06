package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.VerifyOtpRequest;

public interface CreatorService {

    String sendOtp();

    String verifyOtp(VerifyOtpRequest request);
}
