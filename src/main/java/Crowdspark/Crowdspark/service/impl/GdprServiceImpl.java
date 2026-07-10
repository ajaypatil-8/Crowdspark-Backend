// src/main/java/Crowdspark/Crowdspark/service/impl/GdprServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.DataExportResponse;
import Crowdspark.Crowdspark.dto.DeleteAccountRequest;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.AccountStatus;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.*;
import Crowdspark.Crowdspark.service.EmailService;
import Crowdspark.Crowdspark.service.GdprService;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GdprServiceImpl implements GdprService {

    private final UserRepository              userRepository;
    private final DonationRepository          donationRepository;
    private final ProjectRepository           projectRepository;
    private final ProjectCommentRepository    commentRepository;
    private final CampaignUpdateRepository    campaignUpdateRepository;
    private final SavedProjectRepository      savedProjectRepository;
    private final NotificationRepository      notificationRepository;
    private final RefreshTokenService         refreshTokenService;
    private final EmailService                emailService;
    private final PasswordEncoder             passwordEncoder;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE ACCOUNT — GDPR Art. 17 — Right to Erasure
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        // Verify password before doing anything destructive. OAuth-only accounts
        // (Google sign-in) never have a local password set — user.getPassword()
        // is null — and BCryptPasswordEncoder.matches(raw, null) always returns
        // false, so without this branch those users could never pass this check
        // no matter what they typed, permanently locking them out of deleting
        // their own account. The request already arrived on a valid JWT for this
        // exact user, which is the only "credential" an OAuth-only account has.
        boolean hasLocalPassword = user.getPassword() != null && !user.getPassword().isBlank();
        if (hasLocalPassword && !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Incorrect password. Account deletion cancelled.");
        }

        // Prevent double-deletion
        if (user.getAccountStatus() == AccountStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account is already deleted.");
        }

        // Block deletion while a campaign is still live and raising funds: Step 9
        // below wipes bank/UPI details, and payout (Feature #3) has no fallback
        // if the creator is gone by the time the campaign is FUNDED — the project
        // would keep collecting donations with nowhere valid to send the payout.
        List<Project> userProjects = projectRepository.findByCreatorOrderByCreatedAtDesc(user);
        boolean hasLiveCampaign = userProjects.stream()
                .anyMatch(p -> p.getStatus() == ProjectStatus.APPROVED);
        if (hasLiveCampaign) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have an active campaign that is still raising funds. " +
                    "Please wait until it's funded or closed, or contact support@crowdspark.in.");
        }

        log.info("GDPR account deletion initiated for userId={}", userId);
        if (request.getReason() != null && !request.getReason().isBlank()) {
            // Logged (not stored against the user record, which is about to be
            // anonymised) so exit-reason trends are still visible in aggregate
            // via log analytics, per this field's intent.
            log.info("Deletion reason for userId={}: {}", userId, request.getReason());
        }

        // ── Step 1: Revoke all sessions ───────────────────────────────────────
        refreshTokenService.revokeAll(userId);

        // ── Step 2: Cancel PENDING donations (not yet paid) ───────────────────
        List<Donation> pendingDonations = donationRepository
                .findByBacker_IdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(d -> d.getPaymentStatus() == PaymentStatus.PENDING)
                .toList();
        pendingDonations.forEach(d -> {
            d.setPaymentStatus(PaymentStatus.FAILED);
            donationRepository.save(d);
        });
        log.info("Cancelled {} pending donations for userId={}", pendingDonations.size(), userId);

        // ── Step 3: Handle projects created by this user ──────────────────────
        // (userProjects already fetched above; the APPROVED/live case is blocked
        // before we ever get here, so only PENDING/DRAFT/FUNDED/FAILED/REJECTED
        // can appear in this loop)
        for (Project project : userProjects) {
            if (project.getStatus() == ProjectStatus.PENDING
                    || project.getStatus() == ProjectStatus.DRAFT) {
                // Cancel unapproved campaigns
                project.setStatus(ProjectStatus.REJECTED);
                project.setRejectionReason("Creator account deleted");
                projectRepository.save(project);
            }
            // FUNDED / FAILED / REJECTED projects: keep visible with anonymous creator
        }
        log.info("Processed {} projects for userId={}", userProjects.size(), userId);

        // ── Step 4: Soft-delete user's comments (replace content with [deleted]) ─
        commentRepository.findByAuthor_IdOrderByCreatedAtDesc(userId)
                .forEach(comment -> {
                    comment.setDeleted(true);
                    comment.setContent("[deleted]");
                    commentRepository.save(comment);
                });

        // ── Step 5: Soft-delete campaign updates ─────────────────────────────
        campaignUpdateRepository.findByAuthor_IdOrderByCreatedAtDesc(userId)
                .forEach(update -> {
                    update.setTitle("[deleted]");
                    update.setContent("[deleted]");
                    campaignUpdateRepository.save(update);
                });

        // ── Step 6: Delete saved projects ────────────────────────────────────
        savedProjectRepository.findByUser_IdOrderBySavedAtDesc(userId)
                .forEach(savedProjectRepository::delete);

        // ── Step 7: Delete notifications ─────────────────────────────────────
        notificationRepository.deleteAllByRecipientId(userId);

        // ── Step 8: Send final confirmation email BEFORE wiping email ─────────
        String userEmail = user.getEmail();
        String userName  = user.getName() != null ? user.getName() : user.getUsername();
        try {
            emailService.sendSimpleEmail(
                    userEmail,
                    "Your CrowdSpark account has been deleted",
                    "Hi " + userName + ",\n\n" +
                    "Your CrowdSpark account and all associated personal data have been " +
                    "permanently deleted as requested.\n\n" +
                    "We have kept anonymised records of any financial transactions " +
                    "(donations you made or received) as required by Indian tax law.\n\n" +
                    "If you believe this was a mistake, please contact support@crowdspark.in " +
                    "within 30 days — we may be able to recover your account in some cases.\n\n" +
                    "Thank you for being part of CrowdSpark.\n\nTeam CrowdSpark"
            );
        } catch (Exception e) {
            log.warn("Could not send deletion confirmation email to userId={}: {}", userId, e.getMessage());
        }

        // ── Step 9: Anonymise user record (replace all PII) ───────────────────
        String anon = "deleted_" + userId;
        user.setName("Deleted User");
        user.setUsername(anon);
        user.setEmail(anon + "@deleted.crowdspark.in");
        user.setPhoneNumber(null);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // unguessable
        user.setProvider(null);
        user.setProviderId(null);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setKycVerified(false);
        user.setProfileImageUrl(null);
        user.setProfileImagePublicId(null);
        user.setBannerImageUrl(null);
        user.setBannerImagePublicId(null);
        user.setBio(null);
        user.setAbout(null);
        user.setGender(null);
        user.setDateOfBirth(null);
        user.setAddressLine(null);
        user.setCity(null);
        user.setState(null);
        user.setCountry(null);
        user.setPincode(null);
        user.setPlaceId(null);
        user.setLatitude(null);
        user.setLongitude(null);
        user.setProfession(null);
        user.setOrganization(null);
        user.setWebsiteUrl(null);
        user.setLinkedinUrl(null);
        user.setInstagramUrl(null);
        user.setTwitterUrl(null);
        user.setUpiId(null);
        user.setBankName(null);
        user.setMaskedBankAccount(null);
        user.setBankIfscCode(null);
        user.setInterestedCategories(new java.util.HashSet<>());
        user.setRoles(new java.util.HashSet<>());
        user.setAccountStatus(AccountStatus.DELETED);
        user.setEnabled(false);
        user.setLocked(true);

        userRepository.save(user);
        log.info("GDPR account deletion COMPLETED for userId={} → anonymised as '{}'", userId, anon);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DATA EXPORT — GDPR Art. 20 — Right to Data Portability
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DataExportResponse exportData(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        log.info("GDPR data export requested for userId={}", userId);

        // ── Profile ───────────────────────────────────────────────────────────
        DataExportResponse.ProfileData profile = DataExportResponse.ProfileData.builder()
                .id(user.getId())
                .name(user.getName())
                .username(user.getUsername())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .bio(user.getBio())
                .about(user.getAbout())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .dateOfBirth(user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null)
                .city(user.getCity())
                .state(user.getState())
                .country(user.getCountry())
                .profession(user.getProfession())
                .organization(user.getOrganization())
                .websiteUrl(user.getWebsiteUrl())
                .linkedinUrl(user.getLinkedinUrl())
                .instagramUrl(user.getInstagramUrl())
                .twitterUrl(user.getTwitterUrl())
                .accountStatus(user.getAccountStatus().name())
                .createdAt(fmt(user.getCreatedAt()))
                .lastLoginAt(fmt(user.getLastLoginAt()))
                .totalProjectsBacked(user.getTotalProjectsBacked())
                .totalAmountBacked(user.getTotalAmountBacked())
                .totalProjectsCreated(user.getTotalProjectsCreated())
                .totalFundsRaised(user.getTotalFundsRaised())
                .build();

        // ── Donations ─────────────────────────────────────────────────────────
        List<DataExportResponse.DonationData> donations =
                donationRepository.findByBacker_IdOrderByCreatedAtDesc(userId)
                        .stream()
                        .map(d -> DataExportResponse.DonationData.builder()
                                .id(d.getId())
                                .projectId(d.getProject().getId())
                                .projectTitle(d.getProject().getTitle())
                                .amount(d.getAmount())
                                .paymentStatus(d.getPaymentStatus().name())
                                .createdAt(fmt(d.getCreatedAt()))
                                .paidAt(fmt(d.getPaidAt()))
                                .build())
                        .toList();

        // ── Created Projects ──────────────────────────────────────────────────
        List<DataExportResponse.ProjectSummaryData> createdProjects =
                projectRepository.findByCreatorOrderByCreatedAtDesc(user)
                        .stream()
                        .map(p -> DataExportResponse.ProjectSummaryData.builder()
                                .id(p.getId())
                                .title(p.getTitle())
                                .status(p.getStatus().name())
                                .goalAmount(p.getGoalAmount())
                                .currentAmount(p.getCurrentAmount())
                                .createdAt(fmt(p.getCreatedAt()))
                                .deadline(fmt(p.getDeadline()))
                                .build())
                        .toList();

        // ── Saved Projects ────────────────────────────────────────────────────
        List<DataExportResponse.ProjectSummaryData> savedProjects =
                savedProjectRepository.findByUser_IdOrderBySavedAtDesc(userId)
                        .stream()
                        .map(sp -> DataExportResponse.ProjectSummaryData.builder()
                                .id(sp.getProject().getId())
                                .title(sp.getProject().getTitle())
                                .status(sp.getProject().getStatus().name())
                                .goalAmount(sp.getProject().getGoalAmount())
                                .currentAmount(sp.getProject().getCurrentAmount())
                                .createdAt(fmt(sp.getSavedAt()))
                                .build())
                        .toList();

        // ── Comments ──────────────────────────────────────────────────────────
        List<DataExportResponse.CommentData> comments =
                commentRepository.findByAuthor_IdOrderByCreatedAtDesc(userId)
                        .stream()
                        .filter(c -> !c.isDeleted())
                        .map(c -> DataExportResponse.CommentData.builder()
                                .id(c.getId())
                                .projectId(c.getProject().getId())
                                .projectTitle(c.getProject().getTitle())
                                .content(c.getContent())
                                .createdAt(fmt(c.getCreatedAt()))
                                .build())
                        .toList();

        return DataExportResponse.builder()
                .exportedAt(fmt(LocalDateTime.now()))
                .profile(profile)
                .donations(donations)
                .createdProjects(createdProjects)
                .savedProjects(savedProjects)
                .comments(comments)
                .build();
    }

    private String fmt(LocalDateTime dt) {
        return dt != null ? dt.format(FMT) : null;
    }
}
