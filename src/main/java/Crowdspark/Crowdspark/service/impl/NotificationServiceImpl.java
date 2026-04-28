package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.NotificationResponse;
import Crowdspark.Crowdspark.entity.Notification;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.NotificationType;
import Crowdspark.Crowdspark.repository.NotificationRepository;
import Crowdspark.Crowdspark.service.NotificationService;
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

    /** Format ₹ amount: show in L (lakh) if >= 1,00,000, else plain */
    private String fmt(Double amount) {
        if (amount == null) return "₹0";
        if (amount >= 100_000) return String.format("₹%.1fL", amount / 100_000);
        if (amount >= 1_000)   return String.format("₹%.0fK", amount / 1_000);
        return String.format("₹%.0f", amount);
    }

    // ── trigger methods (all @Async — fire-and-forget, never blocks HTTP) ─────

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
        // Also notify backer that their donation was confirmed
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
            "\"" + project.getTitle() + "\" was not approved. Reason: " + reason
                + ". You can edit and resubmit from your dashboard.",
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
            "Your identity verification is complete. You can now create and launch campaigns.",
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
}
