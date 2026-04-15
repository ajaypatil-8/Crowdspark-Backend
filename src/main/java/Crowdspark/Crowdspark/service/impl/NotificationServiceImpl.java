package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.NotificationService;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void notifyCreatorBacked(Project project, User backer, Double amount) {
        // Section 5 — full implementation later
        System.out.println("Notification: " + backer.getUsername()
                + " backed project [" + project.getTitle()
                + "] with ₹" + amount);
    }
}
