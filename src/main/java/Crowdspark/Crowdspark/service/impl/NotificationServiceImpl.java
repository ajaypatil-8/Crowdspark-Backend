// src/main/java/Crowdspark/Crowdspark/service/impl/NotificationServiceImpl.java
// CHANGE: Added 4 new @Async methods at the bottom for deadline scheduler events

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.NotificationResponse;
import Crowdspark.Crowdspark.entity.Notification;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.NotificationType;
import Crowdspark.Crowdspark.repository.NotificationRepository;
import Crowdspark.Crowdspark.service.NotificationService;
import Crowdspark.Crowdspark.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    // BUG FIX (Feature #22): sendToUser() in PushNotificationServiceImpl was
    // fully built (FCM send, stale-token cleanup, etc.) but never actually
    // called from anywhere — the app could register a browser for push, but
    // no push was ever sent for any event. Wiring this in below for the two
    // triggers the feature spec explicitly names: "push on campaign update,
    // funding milestone".
    private final PushNotificationService pushNotificationService;

    // ── internal helper ───────────────────────────────────────────────────────
    private void save(User recipient, NotificationType type,
                      String title, String message, String link, Long referenceId) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        n.setReferenceId(referenceId);
        notificationRepository.save(n);
    }

    private String fmt(Double amount) {
        if (amount == null) return "₹0";
        if (amount >= 100_000) return String.format("₹%.1fL", amount / 100_000);
        if (amount >= 1_000)   return String.format("₹%.0fK", amount / 1_000);
        return String.format("₹%.0f", amount);
    }

    // ── existing trigger methods ──────────────────────────────────────────────

    @Async
    @Override
    public void notifyCreatorBacked(Project project, User backer, Double amount) {
        save(
                project.getCreator(),
                NotificationType.PROJECT_BACKED,
                "New backer! 🎉",
                backer.getName() + " (@" + backer.getUsername() + ") backed \""
                        + project.getTitle() + "\" with " + fmt(amount),
                "/projects/" + project.getId(),
                project.getId()
        );
        notifyBackerDonationConfirmed(backer, project, amount);
    }

    @Async
    @Override
    public void notifyCreatorGoalReached(Project project) {
        save(
                project.getCreator(),
                NotificationType.PROJECT_GOAL_REACHED,
                "🏆 Goal reached!",
                "Congratulations! \"" + project.getTitle() + "\" has hit its funding goal of "
                        + fmt(project.getGoalAmount()) + ". The campaign is now closed.",
                "/dashboard/my-campaigns",
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyCreatorProjectApproved(Project project) {
        save(
                project.getCreator(),
                NotificationType.PROJECT_APPROVED,
                "Project approved ✅",
                "Your project \"" + project.getTitle() + "\" is live and accepting backers!",
                "/projects/" + project.getId(),
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyCreatorProjectRejected(Project project, String reason) {
        save(
                project.getCreator(),
                NotificationType.PROJECT_REJECTED,
                "Project not approved",
                "\"" + project.getTitle() + "\" was not approved. Reason: " + reason,
                "/dashboard/my-campaigns",
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyUserKycApproved(User user) {
        save(
                user,
                NotificationType.KYC_APPROVED,
                "KYC approved ✅",
                "Your identity verification is complete. You can now launch campaigns!",
                "/dashboard/create-campaign",
                user.getId()
        );
    }

    @Async
    @Override
    public void notifyUserKycRejected(User user, String reason) {
        save(
                user,
                NotificationType.KYC_REJECTED,
                "KYC not approved",
                "Your KYC was not approved. Reason: " + reason + ". Please update and resubmit.",
                "/dashboard/profile",
                user.getId()
        );
    }

    @Async
    @Override
    public void notifyBackerDonationConfirmed(User backer, Project project, Double amount) {
        save(
                backer,
                NotificationType.DONATION_CONFIRMED,
                "Donation confirmed ✅",
                "Your contribution of " + fmt(amount) + " to \""
                        + project.getTitle() + "\" is confirmed. Thank you for backing!",
                "/projects/" + project.getId(),
                project.getId()
        );
    }

    // ── NEW: deadline scheduler notifications ─────────────────────────────────

    @Async
    @Override
    public void notifyCreatorCampaignFunded(Project project) {
        save(
                project.getCreator(),
                NotificationType.CAMPAIGN_FUNDED,
                "🎉 Campaign successfully funded!",
                "\"" + project.getTitle() + "\" has closed and raised "
                        + fmt(project.getCurrentAmount()) + " of its "
                        + fmt(project.getGoalAmount()) + " goal. "
                        + "Funds will be disbursed to you shortly.",
                "/dashboard/my-campaigns",
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyCreatorCampaignFailed(Project project) {
        save(
                project.getCreator(),
                NotificationType.CAMPAIGN_FAILED,
                "Campaign ended without reaching goal",
                "\"" + project.getTitle() + "\" raised "
                        + fmt(project.getCurrentAmount()) + " of its "
                        + fmt(project.getGoalAmount()) + " goal. "
                        + "The campaign has closed and backers will be refunded.",
                "/dashboard/my-campaigns",
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyBackerCampaignFunded(User backer, Project project) {
        save(
                backer,
                NotificationType.CAMPAIGN_FUNDED,
                "✅ A project you backed was funded!",
                "\"" + project.getTitle() + "\" successfully reached its goal of "
                        + fmt(project.getGoalAmount()) + ". Thanks for making it happen!",
                "/projects/" + project.getId(),
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyBackerCampaignFailed(User backer, Project project) {
        save(
                backer,
                NotificationType.CAMPAIGN_FAILED,
                "A project you backed didn't reach its goal",
                "\"" + project.getTitle() + "\" raised "
                        + fmt(project.getCurrentAmount()) + " of its "
                        + fmt(project.getGoalAmount()) + " goal. "
                        + "Your contribution will be fully refunded.",
                "/dashboard/backed",
                project.getId()
        );
    }

    // ── REST-facing methods ───────────────────────────────────────────────────

    @Override
    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository
                .findByRecipient_IdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipient_IdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long notificationId, Long userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        assertOwner(n, userId);
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
        return toResponse(n);
    }

    @Override
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllReadByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        assertOwner(n, userId);
        notificationRepository.delete(n);
    }

    @Override
    @Transactional
    public void deleteAllNotifications(Long userId) {
        notificationRepository.deleteAllByRecipientId(userId);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void assertOwner(Notification n, Long userId) {
        if (!n.getRecipient().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .message(n.getMessage())
                .link(n.getLink())
                .referenceId(n.getReferenceId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }

    @Async
    @Override
    public void notifyCreatorPayoutInitiated(Project project, Double netAmount) {
        save(
                project.getCreator(),
                NotificationType.PAYOUT_INITIATED,
                "💸 Your payout is on the way!",
                "A payout of " + fmt(netAmount) + " for \""
                        + project.getTitle() + "\" has been initiated to your UPI. "
                        + "It typically arrives within a few minutes.",
                "/dashboard/my-campaigns",
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyCreatorPayoutFailed(Project project, String reason) {
        save(
                project.getCreator(),
                NotificationType.PAYOUT_FAILED,
                "⚠️ Payout failed",
                "The payout for \"" + project.getTitle() + "\" could not be processed. "
                        + "Reason: " + (reason != null ? reason : "Unknown error")
                        + ". Please contact support.",
                "/dashboard/my-campaigns",
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyBackerRefundProcessed(User backer, Project project, Double amount) {
        save(
                backer,
                NotificationType.REFUND_PROCESSED,
                "✅ Your refund is on the way!",
                "Your contribution of " + fmt(amount) + " to \""
                        + project.getTitle() + "\" is being refunded. "
                        + "It will reach your account within 5-7 business days depending on your bank.",
                "/dashboard/backed",
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyBackerRefundFailed(User backer, Project project, Double amount, String reason) {
        save(
                backer,
                NotificationType.REFUND_FAILED,
                "⚠️ Refund could not be processed",
                "We were unable to refund your contribution of " + fmt(amount) + " for \""
                        + project.getTitle() + "\". Please contact support@crowdspark.in "
                        + "with your donation details and we'll resolve this immediately.",
                "/dashboard/backed",
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyBackerCampaignUpdate(User backer, Project project, String updateTitle) {
        String title   = "📢 New update from " + project.getTitle();
        String message = "The creator posted: \"" + updateTitle + "\". "
                + "Check it out on the project page.";
        String link    = "/projects/" + project.getId() + "?tab=updates";

        save(backer, NotificationType.CAMPAIGN_UPDATE, title, message, link, project.getId());

        // BUG FIX (Feature #22) — see field comment above.
        pushNotificationService.sendToUser(backer, title, message, link, null);
    }

    @Async
    @Override
    public void notifyCreatorNewComment(Project project, User commenter) {
        save(
                project.getCreator(),
                NotificationType.COMMENT_POSTED,
                "💬 New comment on your campaign",
                "@" + commenter.getUsername() + " commented on \""
                        + project.getTitle() + "\". Tap to view and respond.",
                "/projects/" + project.getId() + "?tab=comments",
                project.getId()
        );
    }

    @Async
    @Override
    public void notifyUserCommentReplied(User recipient, Project project, String replierUsername) {
        save(
                recipient,
                NotificationType.COMMENT_REPLIED,
                "↩️ Someone replied to your comment",
                "@" + replierUsername + " replied to your comment on \""
                        + project.getTitle() + "\".",
                "/projects/" + project.getId() + "?tab=comments",
                project.getId()
        );
    }


    @Async
    @Override
    public void notifyUserNewFollower(User target, User follower) {
        save(
                target,
                NotificationType.NEW_FOLLOWER,
                "🎉 New follower!",
                "@" + follower.getUsername() + " started following you. "
                        + "They'll see your new campaigns in their feed.",
                "/dashboard/profile",
                follower.getId()
        );
    }

    @Override
    @Async
    public void notifyBackerMilestoneCompleted(User backer, Project project,
                                               String milestoneTitle) {
        String title   = "🏁 Milestone reached: " + milestoneTitle;
        String message = "The campaign \"" + project.getTitle() + "\" just completed a milestone: \""
                + milestoneTitle + "\". Check the project page for details.";
        String link    = "/projects/" + project.getId();

        save(backer, NotificationType.MILESTONE_COMPLETED, title, message, link, project.getId());

        // BUG FIX (Feature #22) — see field comment above.
        pushNotificationService.sendToUser(backer, title, message, link, null);
    }

    @Override
    @Async
    public void sendGeneralNotification(User recipient, String title,
                                        String message, String link, Long referenceId) {
        save(recipient, NotificationType.GENERAL, title, message, link, referenceId);
    }


}