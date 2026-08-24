// src/main/java/Crowdspark/Crowdspark/service/AiService.java
// Feature #39 — AI Campaign Description Generator
// Feature #40 — AI-Powered Project Recommendations
// Feature #41 — AI Campaign Success Predictor
// Feature #42 — AI Support Chatbot
// Feature #43 — AI Fraud & Risk Detection
// (this interface will keep growing with #44-#48 as the other free-AI
// creator/backer tools land)

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CampaignScoreRequest;
import Crowdspark.Crowdspark.dto.CampaignScoreResponse;
import Crowdspark.Crowdspark.dto.GenerateDescriptionRequest;
import Crowdspark.Crowdspark.dto.GenerateDescriptionResponse;
import Crowdspark.Crowdspark.dto.RecommendationsResponse;
import Crowdspark.Crowdspark.dto.SupportChatRequest;
import Crowdspark.Crowdspark.dto.SupportChatResponse;
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

    /**
     * One turn of the public support chat. Stateless -- the frontend holds
     * conversation history and resends it each turn; nothing is persisted
     * server-side. Public/anonymous-friendly (no user parameter), protected
     * by IP-based rate limiting in RateLimitFilter instead of a per-user cap.
     */
    SupportChatResponse handleSupportChat(SupportChatRequest request);

    /**
     * Queues an async fraud/risk scan for a just-submitted project. Never
     * blocks the caller (ProjectServiceImpl.createProject()) on a Groq call —
     * enqueues onto the "ai-fraud-scan" Redis queue and returns immediately;
     * FraudScanJobWorker does the actual scoring off-thread. If Redis is
     * unavailable, RedisQueueService's own fallback runs the scan directly
     * on a background executor instead, same durability tradeoff every other
     * queued job in this app already accepts.
     */
    void queueFraudScan(Long projectId);
}

