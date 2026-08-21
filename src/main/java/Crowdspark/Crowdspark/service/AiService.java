// src/main/java/Crowdspark/Crowdspark/service/AiService.java
// Feature #39 — AI Campaign Description Generator
// (this interface will grow with #40-#48 as the other Claude-powered features land)

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.GenerateDescriptionRequest;
import Crowdspark.Crowdspark.dto.GenerateDescriptionResponse;

public interface AiService {

    /**
     * Turns a campaign title + a creator's rough bullet points into a drafted
     * short pitch, full Markdown story, and a suggested funding goal.
     *
     * @param request   title + bullet points (+ optional category/location)
     * @param creatorId used only to key the per-creator daily usage limit
     */
    GenerateDescriptionResponse generateCampaignDescription(GenerateDescriptionRequest request, Long creatorId);
}
