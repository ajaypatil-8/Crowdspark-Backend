package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.BackerDashboardResponse;

public interface BackerService {

    /** All projects the logged-in user has backed (any payment status). */
    BackerDashboardResponse getDashboard(Long userId);
}
