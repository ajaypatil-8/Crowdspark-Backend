package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.queue.RedisQueueService;
import Crowdspark.Crowdspark.service.PdfReceiptService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    private static final String FROM_ADDRESS = "hello@crowdspark.in";
    private static final String FROM_NAME = "CrowdSpark";

    @Mock private JavaMailSender mailSender;
    @Mock private TemplateEngine templateEngine;
    @Mock private PdfReceiptService pdfReceiptService;
    @Mock private RedisQueueService queueService;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender, templateEngine, pdfReceiptService, queueService);
        ReflectionTestUtils.setField(emailService, "fromEmail", FROM_ADDRESS);
        ReflectionTestUtils.setField(emailService, "fromName", FROM_NAME);
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://crowdspark.vercel.app");
    }

    @Test
    void otpEmail_usesConfiguredFromAddress_notSmtpLogin() throws Exception {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        when(templateEngine.process(anyString(), any())).thenReturn("<html><body>stub</body></html>");

        emailService.sendOtpEmailNow("backer@example.com", "Asha", "482913", 10);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getFrom()[0].toString()).contains(FROM_ADDRESS);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("backer@example.com");
        assertThat(sent.getSubject()).contains("482913");
    }

    @Test
    void simpleEmail_nowSetsFromExplicitly_insteadOfRelyingOnProviderDefault() {
        emailService.sendSimpleEmailNow("user@example.com", "Verify your CrowdSpark email", "click here");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getFrom()).isEqualTo(FROM_ADDRESS);
        assertThat(captor.getValue().getTo()).containsExactly("user@example.com");
    }

    @Test
    void welcomeEmail_buildsMimeMessage_andSendsThroughJavaMailSender() throws Exception {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        when(templateEngine.process(anyString(), any())).thenReturn("<html><body>stub</body></html>");

        emailService.sendWelcomeEmailNow("creator@example.com", "Priya");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("creator@example.com");
    }
}