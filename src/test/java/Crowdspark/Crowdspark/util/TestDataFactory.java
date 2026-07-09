// src/test/java/Crowdspark/Crowdspark/util/TestDataFactory.java
package Crowdspark.Crowdspark.util;

import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.type.Role;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Centralized factory for test objects.
 * All entities are pre-filled with valid data so tests only
 * override what they specifically need to test.
 */
public class TestDataFactory {

    // ── Users ─────────────────────────────────────────────────────────────────

    public static User backerUser() {
        User u = new User();
        u.setId(1L);
        u.setName("Test Backer");
        u.setUsername("testbacker");
        u.setEmail("backer@test.com");
        u.setPassword("$2a$10$dummyHashedPassword");
        u.setEmailVerified(true);
        u.setKycVerified(false);
        u.setRoles(Set.of(Role.BACKER));
        u.setTotalProjectsBacked(0);
        u.setTotalAmountBacked(0.0);
        u.setTotalProjectsCreated(0);
        u.setTotalFundsRaised(0.0);
        return u;
    }

    public static User creatorUser() {
        User u = new User();
        u.setId(2L);
        u.setName("Test Creator");
        u.setUsername("testcreator");
        u.setEmail("creator@test.com");
        u.setPassword("$2a$10$dummyHashedPassword");
        u.setEmailVerified(true);
        u.setKycVerified(true);
        u.setUpiId("creator@upi");
        u.setRoles(Set.of(Role.CREATOR));
        u.setTotalProjectsBacked(0);
        u.setTotalAmountBacked(0.0);
        u.setTotalProjectsCreated(1);
        u.setTotalFundsRaised(0.0);
        return u;
    }

    public static User adminUser() {
        User u = new User();
        u.setId(3L);
        u.setName("Admin");
        u.setUsername("admin");
        u.setEmail("admin@test.com");
        u.setPassword("$2a$10$dummyHashedPassword");
        u.setEmailVerified(true);
        u.setRoles(Set.of(Role.ADMIN));
        return u;
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    public static Project approvedProject(User creator) {
        Project p = new Project();
        p.setId(10L);
        p.setTitle("Test Campaign");
        p.setShortDescription("A short description of the test campaign");
        p.setFullDescription("Full story of the campaign goes here");
        p.setGoalAmount(100_000.0);
        p.setCurrentAmount(25_000.0);
        p.setDeadline(LocalDateTime.now().plusDays(30));
        p.setStatus(ProjectStatus.APPROVED);
        p.setCreator(creator);
        p.setCreatedAt(LocalDateTime.now().minusDays(5));
        return p;
    }

    public static Project expiredProject(User creator) {
        Project p = approvedProject(creator);
        p.setId(11L);
        p.setDeadline(LocalDateTime.now().minusHours(1)); // expired
        return p;
    }

    public static Project fundedExpiredProject(User creator) {
        Project p = expiredProject(creator);
        p.setId(12L);
        p.setCurrentAmount(p.getGoalAmount()); // goal reached
        return p;
    }

    // ── Donations ─────────────────────────────────────────────────────────────

    public static Donation successfulDonation(User backer, Project project) {
        Donation d = new Donation();
        d.setId(100L);
        d.setBacker(backer);
        d.setProject(project);
        d.setAmount(5_000.0);
        d.setPaymentStatus(PaymentStatus.SUCCESS);
        d.setTransactionId("pay_testPaymentId123");
        d.setRazorpayOrderId("order_testOrderId123");
        d.setPaidAt(LocalDateTime.now().minusHours(1));
        d.setCreatedAt(LocalDateTime.now().minusHours(1));
        return d;
    }

    public static Donation pendingDonation(User backer, Project project) {
        Donation d = successfulDonation(backer, project);
        d.setId(101L);
        d.setPaymentStatus(PaymentStatus.PENDING);
        d.setTransactionId(null);
        d.setPaidAt(null);
        return d;
    }
}
