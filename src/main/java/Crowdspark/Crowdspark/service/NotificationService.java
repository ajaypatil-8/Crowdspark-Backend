// src/main/java/Crowdspark/Crowdspark/service/NotificationService.java
// CHANGE: Added 4 new deadline notification methods for scheduler use

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.NotificationResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    // ── payment / backing events ──────────────────────────────────────────────
    void notifyCreatorBacked(Project project, User backer, Double amount);
    void notifyCreatorGoalReached(Project project);
    void notifyCreatorProjectApproved(Project project);
    void notifyCreatorProjectRejected(Project project, String reason);
    void notifyUserKycApproved(User user);
    void notifyUserKycRejected(User user, String reason);
    void notifyBackerDonationConfirmed(User backer, Project project, Double amount);

    // ── NEW: deadline scheduler events ───────────────────────────────────────
    /** Notify creator their campaign is successfully funded after deadline */
    void notifyCreatorCampaignFunded(Project project);

    /** Notify creator their campaign failed to reach goal */
    void notifyCreatorCampaignFailed(Project project);

    /** Notify a single backer the campaign they backed was successfully funded */
    void notifyBackerCampaignFunded(User backer, Project project);

    /** Notify a single backer the campaign failed and they will be refunded */
    void notifyBackerCampaignFailed(User backer, Project project);

    void notifyCreatorPayoutInitiated(Project project, Double netAmount);
    void notifyCreatorPayoutFailed(Project project, String reason);

    // ── REST-facing methods ───────────────────────────────────────────────────
    Page<NotificationResponse> getNotifications(Long userId, Pageable pageable);
    long             getUnreadCount(Long userId);
    NotificationResponse markRead(Long notificationId, Long userId);
    int              markAllRead(Long userId);
    void             deleteNotification(Long notificationId, Long userId);
    void             deleteAllNotifications(Long userId);
}
