package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.service.EmailService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // OTP
    @Override
    @Async("emailTaskExecutor")
    public void sendOtpEmail(String toEmail, String name, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("CrowdSpark OTP");
        message.setText("Hi " + name + ",\n\nYour OTP: " + otp + "\nValid for 10 minutes.\n\nTeam CrowdSpark");
        mailSender.send(message);
    }

    // mail
    @Override
    @Async("emailTaskExecutor")
    public void sendSimpleEmail(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}