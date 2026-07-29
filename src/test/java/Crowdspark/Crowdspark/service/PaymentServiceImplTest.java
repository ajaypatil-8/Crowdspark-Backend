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
import Crowdspark.Crowdspark.entity.type.Role;
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
import java.util.Set;

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
    @Mock FundingStreamService  fundingStreamService;
    @Mock RewardClaimService    rewardClaimService;
    @Mock EmailService          emailService;
    @Mock PdfReceiptService     pdfReceiptService;

    @InjectMocks PaymentServiceImpl paymentService;

    private User    backer;
    private Project project;

    private static final String TEST_KEY_SECRET = "stubsecretkey12345678901234";
    // AUDIT FIX: matches TestDataFactory.successfulDonation()/pendingDonation()'s
    // hardcoded razorpayOrderId — tests exercising the legitimate/success path
    // now use this same order ID, since verifyAndConfirm's new order-ID check
    // (see verifyAndConfirm_throws400_whenOrderIdDoesNotMatchDonation below)
    // requires the request's orderId to match what's actually stored on the
    // donation, which a real, non-attack checkout flow always satisfies.
    private static final String DONATION_ORDER_ID = "order_testOrderId123";

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

    // ─── verifyAndConfirm — order/donation binding (AUDIT FIX, Feature #1/#2) ──

    @Test
    @DisplayName("verifyAndConfirm throws 400 when the request's orderId doesn't match the donation's stored orderId")
    void verifyAndConfirm_throws400_whenOrderIdDoesNotMatchDonation() throws Exception {
        // This is the exact scenario the fix closes: donation was created for
        // DONATION_ORDER_ID, but the request presents a GENUINELY VALID
        // signature — just for a different order the caller separately, and
        // legitimately, paid for. Before the fix, a valid signature alone was
        // enough to mark ANY donation SUCCESS regardless of which order it was
        // actually for.
        Donation donation = TestDataFactory.pendingDonation(backer, project);
        given(donationRepository.findById(donation.getId())).willReturn(Optional.of(donation));

        String wrongOrderId = "order_aDifferentOrderTheCallerActuallyPaidFor";
        String paymentId    = "pay_xyz789";
        String validSigForWrongOrder = generateHmac(wrongOrderId + "|" + paymentId, TEST_KEY_SECRET);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(donation.getId());
        req.setRazorpayOrderId(wrongOrderId);
        req.setRazorpayPaymentId(paymentId);
        req.setRazorpaySignature(validSigForWrongOrder);

        assertThatThrownBy(() -> paymentService.verifyAndConfirm(req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match the order");

        // Must never be marked SUCCESS off the back of a signature for a different order
        verify(donationRepository, never()).save(argThat(d -> d.getPaymentStatus() == PaymentStatus.SUCCESS));
    }

    // ─── verifyAndConfirm — HMAC signature ───────────────────────────────────

    @Test
    @DisplayName("verifyAndConfirm throws 400 when Razorpay signature is invalid")
    void verifyAndConfirm_throws400_whenSignatureInvalid() {
        Donation donation = TestDataFactory.pendingDonation(backer, project);
        given(donationRepository.findById(100L)).willReturn(Optional.of(donation));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(100L);
        req.setRazorpayOrderId(DONATION_ORDER_ID);
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

        String paymentId = "pay_xyz789";
        String validSig  = generateHmac(DONATION_ORDER_ID + "|" + paymentId, TEST_KEY_SECRET);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(donation.getId());
        req.setRazorpayOrderId(DONATION_ORDER_ID);
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
    @DisplayName("verifyAndConfirm sets project status to FUNDED (not CLOSED) when the goal is reached")
    void verifyAndConfirm_setsProjectFunded_whenGoalReached() throws Exception {
        // goalAmount=100k, currentAmount=25k (TestDataFactory.approvedProject) —
        // a donation of 75k exactly reaches the goal.
        //
        // AUDIT FIX (Feature #2/#3) regression guard: a campaign that reaches
        // its goal must become FUNDED (payout-eligible). It used to become
        // CLOSED instead — a status neither the deadline scheduler nor
        // PayoutServiceImpl would ever pick back up, permanently stranding the
        // payout for any campaign that succeeded before its deadline.
        Donation donation = TestDataFactory.pendingDonation(backer, project);
        donation.setAmount(75_000.0);
        given(donationRepository.findById(donation.getId())).willReturn(Optional.of(donation));
        given(donationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String paymentId = "pay_goalReached";
        String validSig  = generateHmac(DONATION_ORDER_ID + "|" + paymentId, TEST_KEY_SECRET);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(donation.getId());
        req.setRazorpayOrderId(DONATION_ORDER_ID);
        req.setRazorpayPaymentId(paymentId);
        req.setRazorpaySignature(validSig);

        paymentService.verifyAndConfirm(req, backer.getId());

        verify(projectRepository).save(argThat(p -> p.getStatus() == ProjectStatus.FUNDED));
        verify(notificationService).notifyCreatorGoalReached(any());
    }

    @Test
    @DisplayName("verifyAndConfirm throws 403 when donation belongs to different user")
    void verifyAndConfirm_throws403_whenDonationBelongsToDifferentUser() {
        Donation donation = TestDataFactory.pendingDonation(backer, project);
        given(donationRepository.findById(donation.getId())).willReturn(Optional.of(donation));

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(donation.getId());
        req.setRazorpayOrderId(DONATION_ORDER_ID);
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
        req.setRazorpayOrderId(DONATION_ORDER_ID);
        req.setRazorpayPaymentId("pay_xyz");
        req.setRazorpaySignature("any");

        var response = paymentService.verifyAndConfirm(req, backer.getId());

        assertThat(response.getPaymentStatus()).isEqualTo("SUCCESS");
        // No DB writes for already-confirmed donation
        verify(donationRepository, never()).save(any());
        // ...and no duplicate receipt email either
        verify(emailService, never()).sendBackerReceiptEmail(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("verifyAndConfirm sends the receipt email with the donation's own details")
    void verifyAndConfirm_sendsReceiptEmail_withCorrectDetails() throws Exception {
        Donation donation = TestDataFactory.pendingDonation(backer, project);
        given(donationRepository.findById(donation.getId())).willReturn(Optional.of(donation));
        given(donationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String paymentId = "pay_xyz789";
        String validSig   = generateHmac(DONATION_ORDER_ID + "|" + paymentId, TEST_KEY_SECRET);

        PaymentVerifyRequest req = new PaymentVerifyRequest();
        req.setDonationId(donation.getId());
        req.setRazorpayOrderId(DONATION_ORDER_ID);
        req.setRazorpayPaymentId(paymentId);
        req.setRazorpaySignature(validSig);

        paymentService.verifyAndConfirm(req, backer.getId());

        verify(emailService).sendBackerReceiptEmail(
                eq(backer.getEmail()),
                eq(backer.getName()),
                eq(project.getTitle()),
                eq(project.getId()),
                eq(donation.getId()),
                eq(donation.getAmount()),
                eq(paymentId),
                any(),
                any()
        );
    }

    // ─── confirmFromWebhook (AUDIT FIX, Feature #4) ──────────────────────────

    @Test
    @DisplayName("confirmFromWebhook confirms the matching donation for a valid payment.captured event")
    void confirmFromWebhook_confirmsDonation_forValidCapturedEvent() throws Exception {
        String webhookSecret = "stub-webhook-secret";
        ReflectionTestUtils.setField(paymentService, "razorpayWebhookSecret", webhookSecret);

        Donation donation = TestDataFactory.pendingDonation(backer, project);
        given(donationRepository.findByRazorpayOrderId(DONATION_ORDER_ID))
                .willReturn(Optional.of(donation));
        given(donationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String body = ("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"pay_webhookTest\",\"order_id\":\"" + DONATION_ORDER_ID + "\"}}}}");
        String signature = generateHmac(body, webhookSecret);

        paymentService.confirmFromWebhook(body, signature);

        verify(donationRepository).save(argThat(d -> d.getPaymentStatus() == PaymentStatus.SUCCESS));
    }

    @Test
    @DisplayName("confirmFromWebhook rejects a call whose signature doesn't match")
    void confirmFromWebhook_throws400_whenSignatureInvalid() {
        ReflectionTestUtils.setField(paymentService, "razorpayWebhookSecret", "stub-webhook-secret");
        String body = "{\"event\":\"payment.captured\",\"payload\":{}}";

        assertThatThrownBy(() -> paymentService.confirmFromWebhook(body, "not-a-real-signature"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid webhook signature");

        verify(donationRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmFromWebhook ignores event types other than payment.captured")
    void confirmFromWebhook_ignoresNonCapturedEvents() throws Exception {
        String webhookSecret = "stub-webhook-secret";
        ReflectionTestUtils.setField(paymentService, "razorpayWebhookSecret", webhookSecret);

        String body = "{\"event\":\"payment.failed\",\"payload\":{}}";
        String signature = generateHmac(body, webhookSecret);

        paymentService.confirmFromWebhook(body, signature);

        verifyNoInteractions(donationRepository);
    }

    // ─── getReceiptPdf (FIX #10) ──────────────────────────────────────────────

    @Test
    @DisplayName("getReceiptPdf returns PDF bytes for the donation's own backer")
    void getReceiptPdf_returnsBytes_forOwner() {
        Donation donation = TestDataFactory.successfulDonation(backer, project);
        given(donationRepository.findDetailedById(donation.getId())).willReturn(Optional.of(donation));
        given(pdfReceiptService.generateReceiptPdf(
                eq(donation.getId()), any(), any(), any(), any(), any(), any()))
                .willReturn(new byte[]{1, 2, 3});

        byte[] result = paymentService.getReceiptPdf(donation.getId(), backer.getId());

        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("getReceiptPdf allows an admin to access a receipt they didn't make")
    void getReceiptPdf_allowsAdmin_evenIfNotOwner() {
        Donation donation = TestDataFactory.successfulDonation(backer, project);
        given(donationRepository.findDetailedById(donation.getId())).willReturn(Optional.of(donation));

        User admin = TestDataFactory.adminUser();
        given(userRepository.findById(admin.getId())).willReturn(Optional.of(admin));
        given(pdfReceiptService.generateReceiptPdf(
                eq(donation.getId()), any(), any(), any(), any(), any(), any()))
                .willReturn(new byte[]{9});

        byte[] result = paymentService.getReceiptPdf(donation.getId(), admin.getId());

        assertThat(result).containsExactly(9);
    }

    @Test
    @DisplayName("getReceiptPdf throws 403 for a user who is neither the backer nor an admin")
    void getReceiptPdf_throws403_forUnrelatedUser() {
        Donation donation = TestDataFactory.successfulDonation(backer, project);
        given(donationRepository.findDetailedById(donation.getId())).willReturn(Optional.of(donation));

        User stranger = new User();
        stranger.setId(999L);
        stranger.setRoles(Set.of(Role.BACKER));
        given(userRepository.findById(stranger.getId())).willReturn(Optional.of(stranger));

        assertThatThrownBy(() -> paymentService.getReceiptPdf(donation.getId(), stranger.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("getReceiptPdf throws 400 when the donation hasn't succeeded yet")
    void getReceiptPdf_throws400_whenNotYetSuccessful() {
        Donation donation = TestDataFactory.pendingDonation(backer, project);
        given(donationRepository.findDetailedById(donation.getId())).willReturn(Optional.of(donation));

        assertThatThrownBy(() -> paymentService.getReceiptPdf(donation.getId(), backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("successful payments");
    }

    // ─── helper: generate real HMAC-SHA256 ───────────────────────────────────

    private String generateHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return new String(Hex.encodeHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))));
    }
}
