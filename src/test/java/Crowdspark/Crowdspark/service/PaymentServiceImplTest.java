// src/test/java/Crowdspark/Crowdspark/service/PaymentServiceImplTest.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.PaymentOrderRequest;
import Crowdspark.Crowdspark.dto.PaymentOrderResponse;
import Crowdspark.Crowdspark.dto.PaymentVerifyRequest;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.impl.PaymentServiceImpl;
import Crowdspark.Crowdspark.util.TestDataFactory;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl Tests")
class PaymentServiceImplTest {

    @Mock DonationRepository    donationRepository;
    @Mock ProjectRepository     projectRepository;
    @Mock UserRepository        userRepository;
    @Mock RewardTierRepository  rewardTierRepository;
    @Mock NotificationService   notificationService;

    @InjectMocks PaymentServiceImpl paymentService;

    private User    backer;
    private Project project;

    private static final String TEST_KEY_SECRET = "stubsecretkey12345678901234";

    @BeforeEach
    void setUp() {
        backer  = TestDataFactory.backerUser();
        project = TestDataFactory.approvedProject(TestDataFactory.creatorUser());

        // Inject @Value fields
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId",     "rzp_test_stub");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", TEST_KEY_SECRET);
    }

    // ─── createOrder validation ───────────────────────────────────────────────

    @Test
    @DisplayName("createOrder throws 403 when creator tries to back own project")
    void createOrder_throws403_whenCreatorBacksOwnProject() {
        User creator = project.getCreator();
        given(userRepository.findById(creator.getId())).willReturn(Optional.of(creator));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        PaymentOrderRequest req = new PaymentOrderRequest();
        req.setProjectId(project.getId());
        req.setAmount(1000.0);

        assertThatThrownBy(() -> paymentService.createOrder(req, creator.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot back your own");
    }

    @Test
    @DisplayName("createOrder throws 400 when project is not APPROVED")
    void createOrder_throws400_whenProjectNotApproved() {
        project.setStatus(ProjectStatus.PENDING);
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        PaymentOrderRequest req = new PaymentOrderRequest();
        req.setProjectId(project.getId());
        req.setAmount(1000.0);

        assertThatThrownBy(() -> paymentService.createOrder(req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not accepting donations");
    }

    @Test
    @DisplayName("createOrder throws 400 when deadline has passed")
    void createOrder_throws400_whenDeadlinePassed() {
        project.setDeadline(LocalDateTime.now().minusDays(1));
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        PaymentOrderRequest req = new PaymentOrderRequest();
        req.setProjectId(project.getId());
        req.setAmount(1000.0);

        assertThatThrownBy(() -> paymentService.createOrder(req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("deadline");
    }

    @Test
    @DisplayName("createOrder throws 400 when amount exceeds remaining goal")
    void createOrder_throws400_whenAmountExceedsRemaining() {
        // currentAmount = 25k, goalAmount = 100k → remaining = 75k
        // requesting 80k → exceeds remaining
        given(userRepository.findById(backer.getId())).willReturn(Optional.of(backer));
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        PaymentOrderRequest req = new PaymentOrderRequest();
        req.setProjectId(project.getId());
        req.setAmount(80_000.0);

        assertThatThrownBy(() -> paymentService.createOrder(req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceeds remaining goal");
    }

    // ─── verifyAndConfirm — HMAC signature ───────────────────────────────────

    @Test
    @DisplayName("verifyAndConfirm throws 400 when Razorpay signature is invalid")
    void verifyAndConfirm_throws400_whenSignatureInvalid() {
        Donation donation = TestDataFactory.pendingDonation(backer, project);
        given(donationRepository.findById(100L)).willReturn(Optional.of(donation));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(100L);
        req.setRazorpayOrderId("order_abc123");
        req.setRazorpayPaymentId("pay_xyz789");
        req.setRazorpaySignature("invalid_signature_that_wont_match");

        assertThatThrownBy(() -> paymentService.verifyAndConfirm(req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("verification failed");

        // Donation should be marked FAILED
        verify(donationRepository).save(argThat(d -> d.getPaymentStatus() == PaymentStatus.FAILED));
    }

    @Test
    @DisplayName("verifyAndConfirm succeeds with valid HMAC signature")
    void verifyAndConfirm_succeeds_withValidSignature() throws Exception {
        Donation donation = TestDataFactory.pendingDonation(backer, project);
        given(donationRepository.findById(donation.getId())).willReturn(Optional.of(donation));
        given(donationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String orderId   = "order_abc123";
        String paymentId = "pay_xyz789";
        String validSig  = generateHmac(orderId + "|" + paymentId, TEST_KEY_SECRET);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(donation.getId());
        req.setRazorpayOrderId(orderId);
        req.setRazorpayPaymentId(paymentId);
        req.setRazorpaySignature(validSig);

        var response = paymentService.verifyAndConfirm(req, backer.getId());

        assertThat(response.getPaymentStatus()).isEqualTo("SUCCESS");
        assertThat(response.getTransactionId()).isEqualTo(paymentId);

        // Verify project amount updated
        verify(projectRepository).save(argThat(p ->
                p.getCurrentAmount() == (project.getCurrentAmount() + donation.getAmount())));

        // Verify notification fired
        verify(notificationService).notifyCreatorBacked(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("verifyAndConfirm throws 403 when donation belongs to different user")
    void verifyAndConfirm_throws403_whenDonationBelongsToDifferentUser() {
        Donation donation = TestDataFactory.pendingDonation(backer, project);
        given(donationRepository.findById(donation.getId())).willReturn(Optional.of(donation));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(donation.getId());
        req.setRazorpayOrderId("order_abc");
        req.setRazorpayPaymentId("pay_xyz");
        req.setRazorpaySignature("sig");

        Long differentUserId = 999L;
        assertThatThrownBy(() -> paymentService.verifyAndConfirm(req, differentUserId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not your donation");
    }

    @Test
    @DisplayName("verifyAndConfirm is idempotent for already-SUCCESS donation")
    void verifyAndConfirm_isIdempotent_forAlreadySuccessfulDonation() {
        Donation donation = TestDataFactory.successfulDonation(backer, project);
        given(donationRepository.findById(donation.getId())).willReturn(Optional.of(donation));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(donation.getId());
        req.setRazorpayOrderId("order_abc");
        req.setRazorpayPaymentId("pay_xyz");
        req.setRazorpaySignature("any");

        var response = paymentService.verifyAndConfirm(req, backer.getId());

        assertThat(response.getPaymentStatus()).isEqualTo("SUCCESS");
        // No DB writes for already-confirmed donation
        verify(donationRepository, never()).save(any());
    }

    // ─── helper: generate real HMAC-SHA256 ───────────────────────────────────

    private String generateHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return new String(Hex.encodeHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))));
    }
}
