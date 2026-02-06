package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.VerifyOtpRequest;
import Crowdspark.Crowdspark.service.CreatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/creator")
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorService creatorService;

    // 🔥 SEND OTP
    @PostMapping("/send-otp")
    public String sendOtp() {
        return creatorService.sendOtp();
    }

    // 🔥 VERIFY OTP
    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestBody VerifyOtpRequest request) {
        return creatorService.verifyOtp(request);
    }
}
