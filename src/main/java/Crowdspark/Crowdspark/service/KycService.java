package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.KycStatusResponse;
import Crowdspark.Crowdspark.dto.KycSubmitRequest;

import java.util.List;

public interface KycService {


    String sendOtp(Long userId);

    String verifyOtp(Long userId, String otp);

    KycStatusResponse submitKyc(Long userId, KycSubmitRequest request);

    KycStatusResponse getMyKycStatus(Long userId);

    List<KycStatusResponse> getPendingKyc();

    KycStatusResponse approveKyc(Long userId, Long adminId);

    KycStatusResponse rejectKyc(Long userId, Long adminId, String reason);
}