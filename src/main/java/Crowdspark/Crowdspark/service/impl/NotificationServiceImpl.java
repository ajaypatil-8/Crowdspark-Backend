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
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    private void save(User recipient, NotificationType type, String title, String message, String link, Long referenceId) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        n.setReferenceId(referenceId);
        notificationRepository.save(n);
    }

    @Override
    public void notifyCreatorBacked(Project project, User backer, Double amount) {
        save(project.getCreator(), NotificationType.PROJECT_BACKED, "New backer!",
                backer.getUsername() + " backed your project \"" + project.getTitle() + "\" with ₹" + amount,
                "/dashboard/my-campaigns/" + project.getId(), project.getId());
    }

    @Override
    public void notifyCreatorProjectApproved(Project project) {
        save(project.getCreator(), NotificationType.PROJECT_APPROVED, "Project approved 🎉",
                "Your project \"" + project.getTitle() + "\" has been approved and is now live!",
                "/projects/" + project.getId(), project.getId());
    }

    @Override
    public void notifyCreatorProjectRejected(Project project, String reason) {
        save(project.getCreator(), NotificationType.PROJECT_REJECTED, "Project rejected",
                "Your project \"" + project.getTitle() + "\" was rejected. Reason: " + reason,
                "/dashboard/my-campaigns", project.getId());
    }

    @Override
    public void notifyUserKycApproved(User user) {
        save(user, NotificationType.KYC_APPROVED, "KYC approved ✅",
                "Your KYC verification has been approved. You can now create projects.",
                "/dashboard/profile", user.getId());
    }

    @Override
    public void notifyUserKycRejected(User user, String reason) {
        save(user, NotificationType.KYC_REJECTED, "KYC rejected",
                "Your KYC was rejected. Reason: " + reason + ". Please resubmit.",
                "/dashboard/profile", user.getId());
    }

    @Override
    public void notifyBackerDonationConfirmed(User backer, Project project, Double amount) {
        save(backer, NotificationType.DONATION_CONFIRMED, "Donation confirmed ✅",
                "Your donation of ₹" + amount + " to \"" + project.getTitle() + "\" was confirmed.",
                "/projects/" + project.getId(), project.getId());
    }

    @Override
    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipient_IdAndReadFalse(userId);
    }

    @Override
    public NotificationResponse markRead(Long notificationId, Long userId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!n.getRecipient().getId().equals(userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        if (!n.isRead()) { n.setRead(true); n.setReadAt(LocalDateTime.now()); notificationRepository.save(n); }
        return toResponse(n);
    }

    @Override
    public int markAllRead(Long userId) {
        return notificationRepository.markAllReadByUserId(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId()).type(n.getType().name()).title(n.getTitle()).message(n.getMessage())
                .link(n.getLink()).referenceId(n.getReferenceId()).read(n.isRead())
                .createdAt(n.getCreatedAt()).readAt(n.getReadAt()).build();
    }
}