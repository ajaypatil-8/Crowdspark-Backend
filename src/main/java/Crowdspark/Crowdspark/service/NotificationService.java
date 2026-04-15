package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;

public interface NotificationService {
    void notifyCreatorBacked(Project project, User backer, Double amount);
}
