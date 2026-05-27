// src/main/java/Crowdspark/Crowdspark/service/RefundService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.RefundResponse;
import Crowdspark.Crowdspark.entity.Project;

import java.util.List;

public interface RefundService {

    void processRefundsForProject(Project project);

    /** Admin: get all refunds for a project */
    List<RefundResponse> getRefundsForProject(Long projectId);

    /** Backer: get their own refund history */
    List<RefundResponse> getRefundsForBacker(Long backerId);
}
