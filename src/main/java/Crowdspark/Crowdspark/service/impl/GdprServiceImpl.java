// src/main/java/Crowdspark/Crowdspark/service/impl/GdprServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.DataExportResponse;
import Crowdspark.Crowdspark.dto.DeleteAccountRequest;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.KycDocument;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.AccountStatus;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.PayoutStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.*;
import Crowdspark.Crowdspark.service.CloudinaryService;
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

    // AUDIT FIX (Feature #11): these four were never wired in at all. GDPR
    // deletion (built as Feature #11) simply predates Follow/Reviews/Push
    // (Features #18/19/22) and was never revisited once those tables existed
    // — so "erasing" a user left their KYC documents (Aadhaar/PAN numbers and
    // ID images — some of the most sensitive PII in the whole system),
    // follow graph, review text, and push-notification tokens completely
    // untouched.
    private final KycDocumentRepository       kycDocumentRepository;
    private final FcmTokenRepository          fcmTokenRepository;
    private final UserFollowRepository        userFollowRepository;
    private final ProjectReviewRepository     projectReviewRepository;
    private final PayoutRepository            payoutRepository;
    private final CloudinaryService           cloudinaryService;

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
        //
        // AUDIT FIX (Feature #3/#11): this used to check ONLY for APPROVED
        // (still-fundraising) projects. A project that had already reached
        // FUNDED but whose payout admin hadn't triggered yet slipped straight
        // through this guard — the very scenario the comment above is
        // describing — and Step 9 would then wipe upiId/bankName/
        // maskedBankAccount/bankIfscCode anyway, permanently stranding a
        // successful campaign's payout with no way to ever complete it (the
        // project isn't FAILED, so it isn't refund-eligible either). Now also
        // blocked whenever a FUNDED project doesn't yet have a COMPLETED payout.
        List<Project> userProjects = projectRepository.findByCreatorOrderByCreatedAtDesc(user);
        boolean hasLiveCampaign = userProjects.stream()
                .anyMatch(p -> p.getStatus() == ProjectStatus.APPROVED);
        boolean hasUnpaidFundedCampaign = userProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.FUNDED)
                .anyMatch(p -> payoutRepository.findByProject_Id(p.getId())
                        .map(payout -> payout.getStatus() != PayoutStatus.COMPLETED)
                        .orElse(true));
        if (hasLiveCampaign) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have an active campaign that is still raising funds. " +
                    "Please wait until it's funded or closed, or contact support@crowdspark.in.");
        }
        if (hasUnpaidFundedCampaign) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have a successfully funded campaign that hasn't been paid out yet. " +
                    "Please wait until your payout is completed, or contact support@crowdspark.in.");
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
        // (userProjects already fetched above; the APPROVED/live and
        // unpaid-FUNDED cases are both blocked before we ever get here, so only
        // PENDING/DRAFT/FAILED/REJECTED/paid-FUNDED can appear in this loop)
        for (Project project : userProjects) {
            if (project.getStatus() == ProjectStatus.PENDING
                    || project.getStatus() == ProjectStatus.DRAFT) {
                // Cancel unapproved campaigns
                project.setStatus(ProjectStatus.REJECTED);
                project.setRejectionReason("Creator account deleted");
                projectRepository.save(project);
            }
            // FUNDED (already paid out) / FAILED / REJECTED projects: keep visible with anonymous creator
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

        // ── Step 7b (AUDIT FIX, Feature #11/#22): stop push notifications ──────
        // Without this, a "deleted" user's device could keep receiving pushes
        // indefinitely — the account row still exists (anonymised, not
        // dropped), so anything that targets a user by ID still resolves.
        fcmTokenRepository.deleteAllByUserId(userId);

        // ── Step 7c (AUDIT FIX, Feature #11/#18): erase the follow graph ───────
        userFollowRepository.deleteAllInvolvingUser(userId);

        // ── Step 7d (AUDIT FIX, Feature #11/#19): anonymise review text ────────
        // Keep the row (and star rating) so a project's rating/review count
        // stays accurate, same as comments — but the free-text content is
        // this user's own words and gets wiped like everything else here.
        projectReviewRepository.findByReviewer_Id(userId).forEach(review -> {
            review.setTitle(null);
            review.setContent("[deleted]");
            projectReviewRepository.save(review);
        });

        // ── Step 7e (AUDIT FIX, Feature #11): erase KYC documents ──────────────
        // This is the most sensitive PII in the system — Aadhaar/PAN numbers
        // and the actual ID images — so it gets a real delete, including the
        // images on Cloudinary itself, not just an anonymised DB row.
        kycDocumentRepository.findByUserId(userId).ifPresent(kyc -> {
            deleteKycCloudinaryAssets(kyc);
            kycDocumentRepository.delete(kyc);
        });

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

    /** Best-effort cleanup of a KYC document's images on Cloudinary — deleting
     *  the local DB row is not enough on its own to actually erase this PII. */
    private void deleteKycCloudinaryAssets(KycDocument kyc) {
        deletePublicIdQuietly(kyc.getPanCardImagePublicId());
        deletePublicIdQuietly(kyc.getAadhaarFrontPublicId());
        deletePublicIdQuietly(kyc.getAadhaarBackPublicId());
    }

    private void deletePublicIdQuietly(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinaryService.deleteFile(publicId);
        } catch (Exception e) {
            // Don't let a Cloudinary hiccup block the rest of account deletion —
            // log it so it can be cleaned up manually if it ever happens.
            log.warn("Could not delete Cloudinary asset {} during account deletion: {}",
                    publicId, e.getMessage());
        }
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
