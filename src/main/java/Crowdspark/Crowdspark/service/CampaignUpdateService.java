// src/main/java/Crowdspark/Crowdspark/service/CampaignUpdateService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CampaignUpdateRequest;
import Crowdspark.Crowdspark.dto.CampaignUpdateResponse;

import java.util.List;

public interface CampaignUpdateService {

    /** Creator posts a new update for their project */
    CampaignUpdateResponse createUpdate(Long projectId, CampaignUpdateRequest request, Long creatorId);

    /** Public: get all updates for a project (newest first) */
    List<CampaignUpdateResponse> getUpdates(Long projectId);

    /** Creator edits their own update */
    CampaignUpdateResponse editUpdate(Long projectId, Long updateId, CampaignUpdateRequest request, Long creatorId);

    /** Creator deletes their own update */
    void deleteUpdate(Long projectId, Long updateId, Long creatorId);
}
