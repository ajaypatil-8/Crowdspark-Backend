// src/main/java/Crowdspark/Crowdspark/service/AiService.java
// Feature #39 — AI Campaign Description Generator
// Feature #40 — AI-Powered Project Recommendations
// Feature #41 — AI Campaign Success Predictor
// (this interface will keep growing with #42-#48 as the other free-AI
// creator/backer tools land)

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CampaignScoreRequest;
import Crowdspark.Crowdspark.dto.CampaignScoreResponse;
import Crowdspark.Crowdspark.dto.GenerateDescriptionRequest;
import Crowdspark.Crowdspark.dto.GenerateDescriptionResponse;
import Crowdspark.Crowdspark.dto.RecommendationsResponse;
import Crowdspark.Crowdspark.entity.User;

public interface AiService {

    /**
     * Turns a campaign title + a creator's rough bullet points into a drafted
     * short pitch, full Markdown story, and a suggested funding goal.
     *
     * @param request   title + bullet points (+ optional category/location)
     * @param creatorId used only to key the per-creator daily usage limit
     */
    GenerateDescriptionResponse generateCampaignDescription(GenerateDescriptionRequest request, Long creatorId);

    /**
     * Ranks and explains up to 6 live campaigns this backer hasn't already
     * backed or saved, using their backed categories, declared interests,
     * and recently viewed projects as signal. Falls back to trending/recent
     * picks (personalized=false) when there's no signal yet.
     */
    RecommendationsResponse getRecommendations(User user);

    /**
     * Records a project view for personalization only (separate from, and
     * unrelated to, the existing anonymous analytics view counter). Fire and
     * forget -- failures here should never affect the page the user is on.
     */
    void trackRecentlyViewed(Long userId, Long projectId);

    /**
     * Scores a draft campaign 0-100 on likely funding success, with an
     * honest explanation and 3-5 concrete improvement tips. Stateless --
     * evaluates whatever draft data the wizard currently holds, nothing is
     * read from or written to the database.
     *
     * @param request   the assembled draft (title, story, goal, media/reward
     *                  counts, etc.) -- see CampaignScoreRequest
     * @param creatorId used only to key the per-creator daily usage limit
     */
    CampaignScoreResponse predictCampaignSuccess(CampaignScoreRequest request, Long creatorId);
}

