package Crowdspark.Crowdspark.service;

public interface EmailService {

    void sendOtpEmail(String toEmail, String name, String otp);

    void sendSimpleEmail(String toEmail, String subject, String body);
}