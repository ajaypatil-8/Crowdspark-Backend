package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.NotificationResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    // --- trigger methods (called from service layer) ---
    void notifyCreatorBacked(Project project, User backer, Double amount);
    void notifyCreatorProjectApproved(Project project);
    void notifyCreatorProjectRejected(Project project, String reason);
    void notifyUserKycApproved(User user);
    void notifyUserKycRejected(User user, String reason);
    void notifyBackerDonationConfirmed(User backer, Project project, Double amount);

    // --- REST-facing methods ---
    Page<NotificationResponse> getNotifications(Long userId, Pageable pageable);
    long getUnreadCount(Long userId);
    NotificationResponse markRead(Long notificationId, Long userId);
    int markAllRead(Long userId);
}
