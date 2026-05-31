// src/main/java/Crowdspark/Crowdspark/service/RefundService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.RefundResponse;
import Crowdspark.Crowdspark.entity.Project;

import java.util.List;

public interface RefundService {

    void processRefundsForProject(Project project);

    List<RefundResponse> getRefundsForProject(Long projectId);


    List<RefundResponse> getRefundsForBacker(Long backerId);
}
