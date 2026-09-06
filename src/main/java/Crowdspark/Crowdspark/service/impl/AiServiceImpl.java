// src/main/java/Crowdspark/Crowdspark/service/impl/AiServiceImpl.java
// Feature #39 — AI Campaign Description Generator
// Feature #40 — AI-Powered Project Recommendations
// Feature #41 — AI Campaign Success Predictor
// Feature #42 — AI Support Chatbot
// Feature #43 — AI Fraud & Risk Detection
// Feature #44 — AI KYC Document Validation
// Feature #45 — AI Content Moderation
// Feature #46 — AI Campaign Improvement Suggestions
// Feature #47 — AI Auto-Tagging & Category Detection
// Feature #48 — AI Creator Analytics Insights (the last of the 10)
//
// All ten features share one Groq (OpenAI-compatible, free tier) client --
// see callGroq()/callGroqRaw() for text, callGroqVision() for #44's
// image-input calls.
//
// IMPORTANT MODEL CORRECTION (fixed in #44, applies retroactively to
// #39-#43): the default model was "llama-3.3-70b-versatile", which Groq has
// since deprecated -- it no longer appears on Groq's own Supported Models
// page. Default is now "openai/gpt-oss-120b". If you already deployed
// #39-#43 with the old default, just update GROQ_MODEL -- no code changes
// needed, since the model was already externalized as a property.
//
// GROQ_API_KEY is intentionally OPTIONAL at startup (mirrors how
// FirebaseConfig handles a missing FIREBASE_SERVICE_ACCOUNT_PATH): this app
// is already live on Render, and a hard-required @NotBlank in
// AppSecretsProperties would crash the entire backend -- every unrelated
// endpoint included -- the moment any of these features ships, if the key
// hasn't been added on Render yet. Instead, every feature here degrades to
// a clear 503 (or, for the background scans, just logs and skips) until
// the key is set.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CampaignScoreRequest;
import Crowdspark.Crowdspark.dto.CampaignScoreResponse;
import Crowdspark.Crowdspark.dto.CampaignSuggestionsRequest;
import Crowdspark.Crowdspark.dto.CampaignSuggestionsResponse;
import Crowdspark.Crowdspark.dto.CategorySuggestionRequest;
import Crowdspark.Crowdspark.dto.CategorySuggestionResponse;
import Crowdspark.Crowdspark.dto.ChatMessage;
import Crowdspark.Crowdspark.dto.GenerateDescriptionRequest;
import Crowdspark.Crowdspark.dto.GenerateDescriptionResponse;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.dto.RecommendationsResponse;
import Crowdspark.Crowdspark.dto.RecommendedProjectResponse;
import Crowdspark.Crowdspark.dto.SupportChatRequest;
import Crowdspark.Crowdspark.dto.SupportChatResponse;
import Crowdspark.Crowdspark.entity.Category;
import Crowdspark.Crowdspark.entity.ContentModerationCheck;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.KycDocument;
import Crowdspark.Crowdspark.entity.KycDocumentAiCheck;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectComment;
import Crowdspark.Crowdspark.entity.ProjectFraudCheck;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.SavedProject;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.ContentType;
import Crowdspark.Crowdspark.entity.type.FraudCheckStatus;
import Crowdspark.Crowdspark.entity.type.KycCheckStatus;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.entity.type.ModerationStatus;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.queue.RedisQueueService;
import Crowdspark.Crowdspark.repository.CategoryRepository;
import Crowdspark.Crowdspark.repository.ContentModerationCheckRepository;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.KycDocumentAiCheckRepository;
import Crowdspark.Crowdspark.repository.KycDocumentRepository;
import Crowdspark.Crowdspark.repository.ProjectCommentRepository;
import Crowdspark.Crowdspark.repository.ProjectFraudCheckRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.SavedProjectRepository;
import Crowdspark.Crowdspark.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final RestTemplate               restTemplate;
    private final StringRedisTemplate        redisTemplate;
    private final ProjectRepository          projectRepository;
    private final DonationRepository         donationRepository;
    private final SavedProjectRepository     savedProjectRepository;
    private final ProjectFraudCheckRepository projectFraudCheckRepository;
    private final KycDocumentRepository       kycDocumentRepository;
    private final KycDocumentAiCheckRepository kycDocumentAiCheckRepository;
    private final ContentModerationCheckRepository contentModerationCheckRepository;
    private final ProjectCommentRepository   projectCommentRepository;
    private final CategoryRepository         categoryRepository;
    private final RedisQueueService          queueService;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Value("${groq.api-key:}")
    private String apiKey;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String model;

    // Feature #44 — separate from the general "model" above: this is the
    // only vision-capable model on Groq at the moment, and Groq's own docs
    // list it as Preview ("may be discontinued at short notice"), not
    // Production. Kept configurable specifically so a future replacement
    // doesn't require a code change.
    @Value("${groq.vision-model:qwen/qwen3.6-27b}")
    private String visionModel;

    @Value("${groq.max-tokens:2048}")
    private int maxTokens;

    @Value("${groq.temperature:0.8}")
    private double temperature;

    @Value("${groq.base-url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    @Value("${ai.description.daily-limit:25}")
    private int dailyLimit;

    @Value("${ai.success-score.daily-limit:25}")
    private int successScoreDailyLimit;

    @Value("${ai.suggestions.daily-limit:25}")
    private int suggestionsDailyLimit;

    @Value("${ai.category-suggestion.daily-limit:25}")
    private int categorySuggestionDailyLimit;

    private static final DateTimeFormatter DAY_KEY  = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final double            MIN_GOAL = 1_000;
    private static final double            MAX_GOAL = 100_000_000;

    // ═════════════════════════════════════════════════════════════════════
    // Feature #39 — Campaign Description Generator
    // ═════════════════════════════════════════════════════════════════════

    private static final String SYSTEM_PROMPT = """
            You are an expert crowdfunding copywriter for CrowdSpark, a crowdfunding platform for \
            creators in India (similar to Kickstarter or Indiegogo). All amounts are in Indian Rupees (INR).

            You will be given a campaign title and rough, unpolished notes from the creator. Turn them \
            into compelling campaign copy. Rules:
            - Never invent specific facts the creator did not provide - no fake partnerships, awards, \
              press mentions, user counts, or dates.
            - Write in a confident, warm, human tone. Avoid generic hype cliches like revolutionary or \
              game-changing.
            - fullDescription must be well-structured Markdown using ## headers, roughly 300 to 700 words, \
              covering why the project matters, the plan, how funds will be used, and who the creator is - \
              using only details actually given.
            - shortPitch is a punchy one to two sentence hook, maximum 280 characters, no markdown.
            - suggestedGoalAmount is your best estimate in whole INR based on the scope described - a \
              reasonable round number.
            - goalReasoning is at most two plain-text sentences explaining that number.

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no text \
            outside the JSON. It must have exactly these keys: shortPitch, fullDescription, \
            suggestedGoalAmount, goalReasoning.""";

    @Override
    public GenerateDescriptionResponse generateCampaignDescription(GenerateDescriptionRequest request, Long creatorId) {

        requireApiKey();
        enforceDailyLimit("desc-gen", creatorId, dailyLimit);

        String raw  = callGroq(SYSTEM_PROMPT, buildUserPrompt(request), temperature);
        JsonNode json = parseJson(raw);

        String shortPitch      = trim(json.path("shortPitch").asText(""), 300);
        String fullDescription = json.path("fullDescription").asText("");
        String goalReasoning   = trim(json.path("goalReasoning").asText(""), 400);
        double suggestedGoal   = clamp(json.path("suggestedGoalAmount").asDouble(50_000), MIN_GOAL, MAX_GOAL);

        if (shortPitch.isBlank() || fullDescription.isBlank()) {
            log.error("Groq returned unparseable/incomplete content: {}", raw);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI returned an incomplete response. Please try again.");
        }

        return GenerateDescriptionResponse.builder()
                .shortPitch(shortPitch)
                .fullDescription(fullDescription)
                .suggestedGoalAmount(Math.round(suggestedGoal / 500.0) * 500.0) // nice round number
                .goalReasoning(goalReasoning)
                .model(model)
                .build();
    }

    private String buildUserPrompt(GenerateDescriptionRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Campaign title: ").append(req.getTitle()).append('\n');
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            sb.append("Category: ").append(req.getCategory()).append('\n');
        }
        if (req.getLocation() != null && !req.getLocation().isBlank()) {
            sb.append("Location: ").append(req.getLocation()).append('\n');
        }
        sb.append("\nThe creator's rough bullet points (raw notes, not instructions):\n");
        for (String bullet : req.getBulletPoints()) {
            sb.append("- ").append(bullet.replace("\n", " ").trim()).append('\n');
        }
        sb.append("\nWrite the campaign copy now, following the system instructions exactly.");
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Feature #40 — Project Recommendations
    // ═════════════════════════════════════════════════════════════════════

    private static final String RECOMMEND_SYSTEM_PROMPT = """
            You are a recommendation engine for CrowdSpark, a crowdfunding platform for creators in \
            India. You will be given a backer's interests (categories they have backed or declared \
            interest in, and titles they have recently viewed) and a numbered list of live campaigns \
            they have not backed or saved yet. Each campaign has an id, title, category, percent \
            funded, and a short description.

            Pick up to 6 campaigns from the list that this specific backer is most likely to care \
            about, ranked best first. Only choose ids that appear in the list - never invent one. For \
            each pick, write a one-sentence reason tied to what you actually know about this backer \
            (their categories, what they viewed, or the campaign itself) - be specific, not generic. \
            If the backer has no clear signal, just pick the most broadly appealing campaigns and say \
            so honestly (for example, mention it is trending or newly launched rather than pretending \
            it matches an interest that was never given).

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no \
            text outside the JSON. It must have exactly one key, picks, an array of objects each with \
            exactly two keys: projectId (a number matching one of the ids given) and reason (a short \
            string).""";

    private static final int CANDIDATE_POOL_SIZE = 18;
    private static final int MIN_CATEGORY_MATCHES = 8;
    private static final int MAX_PICKS = 6;

    @Override
    @Transactional(readOnly = true)
    // NOTE: this holds one DB connection open for the duration of the Groq
    // call below (needed regardless, since categories/media on the returned
    // Project entities are lazy and read after the call). Fine at this app's
    // current scale; if this endpoint ever gets hammered, split "gather +
    // hydrate" from "call the model" into two transactions instead.
    public RecommendationsResponse getRecommendations(User user) {

        requireApiKey();

        // ── Gather signal ───────────────────────────────────────────────────
        List<Donation> backed = donationRepository.findByBacker_IdOrderByCreatedAtDesc(user.getId());
        Set<Long>   excludeIds      = new HashSet<>();
        Set<String> backedCategories = new LinkedHashSet<>();
        for (Donation d : backed) {
            excludeIds.add(d.getProject().getId());
            for (Category c : d.getProject().getCategories()) backedCategories.add(c.getName());
        }

        List<SavedProject> saved = savedProjectRepository.findByUser_IdOrderBySavedAtDesc(user.getId());
        for (SavedProject s : saved) excludeIds.add(s.getProject().getId());

        List<Long> recentlyViewedIds = getRecentlyViewedIds(user.getId(), 10);
        List<Project> recentlyViewed = recentlyViewedIds.isEmpty()
                ? List.of() : projectRepository.findAllById(recentlyViewedIds);

        Set<String> favoredCategories = new LinkedHashSet<>(backedCategories);
        if (user.getInterestedCategories() != null) favoredCategories.addAll(user.getInterestedCategories());
        for (Project p : recentlyViewed) {
            for (Category c : p.getCategories()) favoredCategories.add(c.getName());
        }
        boolean hasSignal = !favoredCategories.isEmpty() || !recentlyViewed.isEmpty();

        List<Long> excludeList = excludeIds.isEmpty() ? List.of(-1L) : new ArrayList<>(excludeIds);

        // ── Build the candidate pool the model is allowed to choose from ────
        List<Project> candidates = new ArrayList<>();
        if (!favoredCategories.isEmpty()) {
            candidates.addAll(projectRepository.findCandidatesByCategories(
                    ProjectStatus.APPROVED, new ArrayList<>(favoredCategories), excludeList,
                    PageRequest.of(0, CANDIDATE_POOL_SIZE)));
        }
        if (candidates.size() < MIN_CATEGORY_MATCHES) {
            Set<Long> already = candidates.stream().map(Project::getId).collect(Collectors.toSet());
            already.addAll(excludeIds);
            List<Long> topUpExclude = already.isEmpty() ? List.of(-1L) : new ArrayList<>(already);
            candidates.addAll(projectRepository.findRecentCandidates(
                    ProjectStatus.APPROVED, topUpExclude,
                    PageRequest.of(0, CANDIDATE_POOL_SIZE - candidates.size())));
        }

        if (candidates.isEmpty()) {
            // Nothing to recommend platform-wide right now -- don't spend an
            // AI call on an empty pool.
            return RecommendationsResponse.builder().recommendations(List.of()).personalized(false).build();
        }

        // ── Ask the model to pick, rank, and explain ────────────────────────
        Map<Long, Project> byId = candidates.stream()
                .collect(Collectors.toMap(Project::getId, p -> p, (a, b) -> a));

        String raw = getCachedOrFreshPicks(user, backedCategories, recentlyViewed, candidates);
        JsonNode json = parseJson(raw);

        List<RecommendedProjectResponse> picks = new ArrayList<>();
        if (json.path("picks").isArray()) {
            for (JsonNode pick : json.path("picks")) {
                Project p = byId.get(pick.path("projectId").asLong(-1));
                if (p == null) continue; // hallucinated/stale id -- drop it, don't guess
                String reason = trim(pick.path("reason").asText(""), 200);
                picks.add(RecommendedProjectResponse.builder()
                        .project(toFeedResponse(p))
                        .reason(reason.isBlank() ? "Matches your interests" : reason)
                        .build());
                if (picks.size() >= MAX_PICKS) break;
            }
        }

        // Parsing came back empty/unusable -- still show something rather
        // than a blank section; the candidate pool itself is already a
        // reasonable answer even without the model's ranking.
        if (picks.isEmpty()) {
            for (Project p : candidates.stream().limit(MAX_PICKS).toList()) {
                picks.add(RecommendedProjectResponse.builder()
                        .project(toFeedResponse(p))
                        .reason(hasSignal ? "Matches your interests" : "Trending on CrowdSpark")
                        .build());
            }
        }

        return RecommendationsResponse.builder().recommendations(picks).personalized(hasSignal).build();
    }

    /**
     * Only the network call is cache-gated (6h, per user) -- the candidate
     * pool above is always rebuilt fresh so funding numbers never go stale,
     * and a cached pick that's since been backed/expired simply won't be in
     * a fresh byId map and gets dropped by the same guard as a hallucinated id.
     */
    private String getCachedOrFreshPicks(User user, Set<String> backedCategories,
                                          List<Project> recentlyViewed, List<Project> candidates) {
        String cacheKey = "ai:recs:" + user.getId();
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return cached;
        } catch (Exception e) {
            log.warn("Failed to read recommendation cache for user {}: {}", user.getId(), e.getMessage());
        }

        String raw = callGroq(RECOMMEND_SYSTEM_PROMPT,
                buildRecommendationPrompt(user, backedCategories, recentlyViewed, candidates), 0.6);

        try {
            redisTemplate.opsForValue().set(cacheKey, raw, 6, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache recommendations for user {}: {}", user.getId(), e.getMessage());
        }
        return raw;
    }

    private String buildRecommendationPrompt(User user, Set<String> backedCategories,
                                              List<Project> recentlyViewed, List<Project> candidates) {
        StringBuilder sb = new StringBuilder("Backer profile:\n");
        if (!backedCategories.isEmpty()) {
            sb.append("- Has backed projects in: ").append(String.join(", ", backedCategories)).append('\n');
        }
        if (user.getInterestedCategories() != null && !user.getInterestedCategories().isEmpty()) {
            sb.append("- Says they are interested in: ")
              .append(String.join(", ", user.getInterestedCategories())).append('\n');
        }
        if (!recentlyViewed.isEmpty()) {
            sb.append("- Recently viewed: ")
              .append(recentlyViewed.stream().map(Project::getTitle).collect(Collectors.joining(", ")))
              .append('\n');
        }
        if (backedCategories.isEmpty() && recentlyViewed.isEmpty()
                && (user.getInterestedCategories() == null || user.getInterestedCategories().isEmpty())) {
            sb.append("- No signal yet - this backer is new or has not shown a category preference.\n");
        }

        sb.append("\nLive campaigns to choose from:\n");
        for (Project p : candidates) {
            String cat = p.getCategories().isEmpty() ? "Uncategorized" : p.getCategories().get(0).getName();
            int pct = p.getGoalAmount() != null && p.getGoalAmount() > 0
                    ? (int) ((p.getCurrentAmount() / p.getGoalAmount()) * 100) : 0;
            sb.append("id ").append(p.getId())
              .append(" | ").append(p.getTitle())
              .append(" | category: ").append(cat)
              .append(" | ").append(pct).append("% funded")
              .append(" | ").append(shorten(p.getShortDescription(), 140))
              .append('\n');
        }
        return sb.toString();
    }

    /** Mirrors FollowServiceImpl.toFeedResponse's field mapping (kept local
     *  here rather than shared, since that method is private to that class
     *  and this feature doesn't need its creator-hover-card enrichment). */
    private ProjectFeedResponse toFeedResponse(Project p) {
        String thumbnail = null, previewVideo = null;
        for (ProjectMedia m : p.getMedia()) {
            if (m.getUsage() == MediaUsage.THUMBNAIL)  thumbnail    = m.getMediaUrl();
            if (m.getUsage() == MediaUsage.CARD_VIDEO) previewVideo = m.getMediaUrl();
        }
        int pct = p.getGoalAmount() != null && p.getGoalAmount() > 0
                ? (int) ((p.getCurrentAmount() / p.getGoalAmount()) * 100) : 0;
        long daysLeft = ChronoUnit.DAYS.between(java.time.LocalDateTime.now(), p.getDeadline());
        long backers  = donationRepository.countByProject_IdAndPaymentStatus(p.getId(), PaymentStatus.SUCCESS);
        String cat    = p.getCategories().isEmpty() ? null : p.getCategories().get(0).getName();
        User creator  = p.getCreator();

        return ProjectFeedResponse.builder()
                .id(p.getId()).title(p.getTitle())
                .shortDescription(p.getShortDescription())
                .category(cat).thumbnailUrl(thumbnail).previewVideoUrl(previewVideo)
                .goalAmount(p.getGoalAmount()).currentAmount(p.getCurrentAmount())
                .fundedPercentage(pct).daysLeft((int) daysLeft).backersCount(backers)
                .creator(ProjectFeedResponse.CreatorDto.builder()
                        .id(creator.getId())
                        .username(creator.getUsername())
                        .profileImage(creator.getProfileImageUrl())
                        .about(creator.getBio())
                        .joinedAt(creator.getCreatedAt() != null ? creator.getCreatedAt().toString() : null)
                        .totalProjects(creator.getTotalProjectsCreated() != null
                                ? creator.getTotalProjectsCreated().longValue() : 0L)
                        .totalBackers(0L) // per-creator batch count skipped here -- see class comment
                        .build())
                .build();
    }

    private String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max).trim() + "...";
    }

    // ═════════════════════════════════════════════════════════════════════
    // Feature #41 — Campaign Success Predictor
    // ═════════════════════════════════════════════════════════════════════

    private static final String SCORE_SYSTEM_PROMPT = """
            You are a crowdfunding success analyst for CrowdSpark, a platform for creators in India \
            (similar to Kickstarter or Indiegogo). All amounts are in Indian Rupees (INR).

            You will be given a creator's draft campaign, plus a few computed stats about it. Assess \
            how likely this specific campaign is to reach its funding goal, based on factors real \
            crowdfunding data supports: a clear and specific pitch, a well-structured story that \
            explains the plan and how funds are used, a goal that is realistic for the scope \
            described, a reasonable campaign length (roughly 30 to 45 days tends to perform best - \
            much longer or much shorter tends to hurt), a thumbnail image and ideally a video, and \
            having reward tiers for backers to choose from.

            Score from 0 to 100. Give honest, specific, constructive feedback - point out real \
            weaknesses, do not just praise. Base every specific claim only on what you were actually \
            given (the text and the stats), never invent facts about the campaign.

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no \
            text outside the JSON. It must have exactly these keys: score (a whole number 0-100), \
            verdict (a short 2-4 word label like Strong, Promising, Needs Work, or High Risk), \
            explanation (2-3 plain-text sentences on the overall assessment), and tips (an array of \
            3-5 short, specific, actionable strings for what to improve, most impactful first - if \
            the campaign is already strong, tips can be smaller polish suggestions instead of major \
            fixes).""";

    @Override
    public CampaignScoreResponse predictCampaignSuccess(CampaignScoreRequest request, Long creatorId) {

        requireApiKey();
        enforceDailyLimit("success-score", creatorId, successScoreDailyLimit);

        String raw    = callGroq(SCORE_SYSTEM_PROMPT, buildScorePrompt(request), 0.4);
        JsonNode json = parseJson(raw);

        int    score       = (int) clamp(json.path("score").asInt(50), 0, 100);
        String verdict     = trim(json.path("verdict").asText(""), 40);
        String explanation = trim(json.path("explanation").asText(""), 500);

        // Feature #46 factored this exact loop out into extractStringArray()
        // below when it needed the same pattern for three separate fields —
        // this call site was updated to match rather than leaving two copies.
        List<String> tips = extractStringArray(json, "tips", 6, 200);
        if (verdict.isBlank() || explanation.isBlank()) {
            log.error("Groq returned unparseable success-score content: {}", raw);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI returned an incomplete response. Please try again.");
        }

        return CampaignScoreResponse.builder()
                .score(score)
                .verdict(verdict)
                .explanation(explanation)
                .tips(tips)
                .model(model)
                .build();
    }

    private String buildScorePrompt(CampaignScoreRequest req) {
        int wordCount = req.getFullDescription().isBlank()
                ? 0 : req.getFullDescription().trim().split("\\s+").length;

        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(req.getTitle()).append('\n');
        sb.append("Short pitch: ").append(req.getShortDescription()).append('\n');
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            sb.append("Category: ").append(req.getCategory()).append('\n');
        }
        sb.append("Funding goal: INR ").append(String.format("%,.0f", req.getGoalAmount())).append('\n');
        sb.append("Campaign length: ").append(req.getDurationDays()).append(" days\n");
        sb.append("Thumbnail uploaded: ").append(req.isHasThumbnail() ? "yes" : "no").append('\n');
        sb.append("Video uploaded: ").append(req.isHasVideo() ? "yes" : "no").append('\n');
        sb.append("Total media files: ").append(req.getMediaCount()).append('\n');
        sb.append("Reward tiers offered: ").append(req.getRewardTierCount()).append('\n');
        sb.append("Story length: ").append(wordCount).append(" words\n");
        sb.append("\nFull story text:\n").append(req.getFullDescription());
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Feature #42 — Support Chatbot
    // ═════════════════════════════════════════════════════════════════════
    // Public/anonymous-friendly (no @PreAuthorize, added to SecurityConfig's
    // permitAll list) -- unlike #39/#41 this needs to work for visitors who
    // haven't signed up yet. Rate limiting is therefore IP-based via
    // RateLimitFilter (see the new "/api/v1/ai/support-chat" entry there)
    // rather than the per-creator Redis counters used elsewhere in this file
    // -- there's no user id to key on for an anonymous caller.

    private static final String SUPPORT_SYSTEM_PROMPT = """
            You are the support assistant for CrowdSpark, a crowdfunding platform for creators in \
            India (similar to Kickstarter or Indiegogo). All amounts are in Indian Rupees (INR).

            What you know about how CrowdSpark works:
            - All-or-nothing funding: if a campaign does not reach its goal by its deadline, every \
              backer is automatically refunded and the creator receives nothing.
            - Backers pay through Razorpay in INR, can optionally choose a reward tier when backing, \
              and get an email receipt.
            - Creators must complete identity verification (KYC) before launching a campaign, and \
              every campaign is reviewed by an admin before it goes live, which usually takes 24 to \
              48 hours.
            - CrowdSpark takes a platform fee out of funds paid to creators after a successful \
              campaign.
            - Backers can save projects to a personal watchlist and follow creators they like. Only \
              backers who actually funded a project can leave a review on it.
            - Account settings include two-factor authentication and the option to permanently delete \
              an account and its data.

            What you must not do:
            - Never claim to know details about a specific user's account, payment, campaign status, \
              or refund - you have no access to that data. If someone asks about their own order, \
              refund, or account, say you cannot look that up and that they should contact support \
              with their account email or order details.
            - Never invent a policy, fee, or number you were not told here. If you are not sure, say \
              so honestly instead of guessing.
            - Only discuss CrowdSpark. If someone asks something unrelated to the platform, politely \
              say that is outside what you can help with here.

            Keep replies short and conversational - a few sentences, not an essay, unless the \
            question genuinely needs more.

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no \
            text outside the JSON. It must have exactly these keys: reply (your response as plain \
            text, no markdown), and escalate (true if this needs a human - because you do not know \
            the answer, it needs account-specific lookup, or the user asked for a person - false \
            otherwise).""";

    private static final int SUPPORT_CHAT_HISTORY_WINDOW = 12;

    @Override
    public SupportChatResponse handleSupportChat(SupportChatRequest request) {

        requireApiKey();

        List<ChatMessage> all    = request.getMessages();
        List<ChatMessage> recent = all.size() > SUPPORT_CHAT_HISTORY_WINDOW
                ? all.subList(all.size() - SUPPORT_CHAT_HISTORY_WINDOW, all.size())
                : all;

        List<Map<String, String>> conversation = recent.stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        String   raw  = callGroq(SUPPORT_SYSTEM_PROMPT, conversation, 0.5);
        JsonNode json = parseJson(raw);

        String  reply    = trim(json.path("reply").asText(""), 1500);
        boolean escalate = json.path("escalate").asBoolean(false);

        if (reply.isBlank()) {
            log.error("Groq returned unparseable support-chat content: {}", raw);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI returned an incomplete response. Please try again.");
        }

        return SupportChatResponse.builder().reply(reply).escalate(escalate).build();
    }

    // ── Recently-viewed tracking (Redis-only, feeds getRecommendations) ────
    // Deliberately separate from AnalyticsServiceImpl.recordView: that one is
    // anonymous-friendly and privacy-preserving by design (SHA-256 visitor
    // hash, no user link, feeds creator-facing aggregate stats). This one is
    // authenticated-only, per-user, and exists purely so a backer's own
    // recommendations can reflect what they've actually been looking at.

    @Override
    @Async
    public void trackRecentlyViewed(Long userId, Long projectId) {
        try {
            String key = "recently-viewed:" + userId;
            redisTemplate.opsForList().remove(key, 0, String.valueOf(projectId)); // dedupe
            redisTemplate.opsForList().leftPush(key, String.valueOf(projectId));  // newest first
            redisTemplate.opsForList().trim(key, 0, 19);                          // cap at 20
            redisTemplate.expire(key, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to track recently-viewed for user {} project {}: {}", userId, projectId, e.getMessage());
        }
    }

    private List<Long> getRecentlyViewedIds(Long userId, int limit) {
        try {
            List<String> raw = redisTemplate.opsForList().range("recently-viewed:" + userId, 0, limit - 1);
            if (raw == null || raw.isEmpty()) return List.of();
            return raw.stream().map(Long::parseLong).toList();
        } catch (Exception e) {
            log.warn("Failed to read recently-viewed for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Feature #43 — Fraud & Risk Detection
    // ═════════════════════════════════════════════════════════════════════
    // Runs async, off the submission request entirely — see the class-level
    // comment on FraudScanJobWorker for the full worker-loop design. Two
    // methods here: queueFraudScan() is the one other services call (on the
    // public AiService interface); scanProjectForFraud() is what the worker
    // actually invokes -- deliberately NOT on the interface, exact same
    // relationship EmailJobWorker has with EmailServiceImpl's "...Now"
    // methods (concrete-class dependency, not the public service interface).

    // Shared by every async AI job in this file (#43 and #44 so far) — one
    // queue, one worker (AiJobWorker), dispatched by job type, exactly the
    // same shape as EmailJobWorker/EMAIL_QUEUE already uses for its several
    // email job types. Was a fraud-scan-only "ai-fraud-scan" queue with its
    // own single-purpose worker until this feature needed the same async
    // pattern for a second job type — consolidated rather than standing up
    // a second near-identical worker class.
    private static final String AI_JOBS_QUEUE = "ai-jobs";

    private static final String FRAUD_SYSTEM_PROMPT = """
            You are a risk analyst for CrowdSpark, a crowdfunding platform for creators in India \
            (similar to Kickstarter or Indiegogo). All amounts are in Indian Rupees (INR).

            You will be given a newly submitted campaign, awaiting admin approval. Assess how likely \
            it is to be fraudulent, misleading, or otherwise a problem for the platform - not simply \
            how good the campaign copy is. Real crowdfunding fraud and risk patterns include: a goal \
            far too large or too small for the scope actually described, vague or generic \
            descriptions that could describe almost any project, promises of guaranteed profit or \
            investment returns (crowdfunding here is a donation or reward, never an investment - \
            this is a serious red flag), urgency or pressure tactics, claims of partnerships, \
            credentials, or media coverage stated as fact with no way to verify them, and a story \
            that is internally inconsistent.

            A short, simple, honest campaign is not automatically risky - plenty of legitimate \
            campaigns are brief. Score the actual signals present, do not penalize brevity alone.

            Score the risk from 0 to 100, where 0 means no concerns and 100 means strong signs of \
            fraud. Only flag what the text actually supports - never invent a concern you cannot \
            point to in the given content.

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no \
            text outside the JSON. It must have exactly these keys: riskScore (a whole number \
            0-100), riskLevel (exactly one of LOW, MEDIUM, or HIGH), reasoning (2-3 plain-text \
            sentences for the admin reviewing this), and signals (an array of 0-5 short strings, \
            each a specific concern - an empty array is correct when there is nothing notable).""";

    @Override
    public void queueFraudScan(Long projectId) {
        queueService.enqueue(AI_JOBS_QUEUE, "SCAN_PROJECT_FRAUD", new FraudScanPayload(projectId),
                () -> scanProjectForFraud(projectId));
    }

    /** Called by FraudScanJobWorker. Public (not on AiService) so the worker
     *  can reach it directly, same pattern as EmailServiceImpl's "Now" methods. */
    @Transactional
    public void scanProjectForFraud(Long projectId) {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Fraud scan requested for project {} but GROQ_API_KEY is not configured — skipping", projectId);
            return; // background job — nothing is waiting on a response, so just skip rather than throw
        }

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            log.warn("Fraud scan requested for project {} but it no longer exists — skipping", projectId);
            return;
        }

        ProjectFraudCheck check = projectFraudCheckRepository.findByProject_Id(projectId)
                .orElseGet(() -> {
                    ProjectFraudCheck c = new ProjectFraudCheck();
                    c.setProject(project);
                    return c;
                });

        try {
            String   raw  = callGroq(FRAUD_SYSTEM_PROMPT, buildFraudPrompt(project), 0.3);
            JsonNode json = parseJson(raw);

            int    score = (int) clamp(json.path("riskScore").asInt(50), 0, 100);
            String level = trim(json.path("riskLevel").asText("MEDIUM"), 10).toUpperCase();
            if (!level.equals("LOW") && !level.equals("MEDIUM") && !level.equals("HIGH")) level = "MEDIUM";
            String reasoning = trim(json.path("reasoning").asText(""), 1000);

            List<String> signalsList = new ArrayList<>();
            if (json.path("signals").isArray()) {
                for (JsonNode s : json.path("signals")) {
                    String sig = trim(s.asText(""), 200);
                    if (!sig.isBlank()) signalsList.add(sig);
                }
            }

            check.setStatus(FraudCheckStatus.COMPLETED);
            check.setRiskScore(score);
            check.setRiskLevel(level);
            check.setReasoning(reasoning.isBlank() ? "No specific concerns noted." : reasoning);
            check.setSignals(String.join("\n", signalsList));
            check.setModel(model);
            check.setCheckedAt(LocalDateTime.now());

        } catch (Exception e) {
            // Groq down, malformed response, etc. — record FAILED so the admin
            // UI shows "scan unavailable" honestly instead of a wrong score,
            // and so this doesn't get silently retried forever.
            log.error("Fraud scan failed for project {}: {}", projectId, e.getMessage());
            check.setStatus(FraudCheckStatus.FAILED);
            check.setCheckedAt(LocalDateTime.now());
        }

        projectFraudCheckRepository.save(check);
    }

    private String buildFraudPrompt(Project project) {
        long durationDays = Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), project.getDeadline()));
        String categories = project.getCategories().isEmpty() ? "none"
                : project.getCategories().stream().map(Category::getName).collect(Collectors.joining(", "));
        User creator = project.getCreator();
        long accountAgeDays = creator.getCreatedAt() != null
                ? ChronoUnit.DAYS.between(creator.getCreatedAt(), LocalDateTime.now()) : -1;

        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(project.getTitle()).append('\n');
        sb.append("Category: ").append(categories).append('\n');
        sb.append("Location: ").append(project.getLocation() == null || project.getLocation().isBlank()
                ? "not given" : project.getLocation()).append('\n');
        sb.append("Funding goal: INR ").append(String.format("%,.0f", project.getGoalAmount())).append('\n');
        sb.append("Campaign length: ").append(durationDays).append(" days\n");
        sb.append("Media files attached: ").append(project.getMedia().size()).append('\n');
        sb.append("Creator account age: ").append(accountAgeDays >= 0 ? accountAgeDays + " days" : "unknown").append('\n');
        sb.append("Creator's previously created projects: ")
          .append(creator.getTotalProjectsCreated() == null ? 0 : creator.getTotalProjectsCreated()).append('\n');
        sb.append("\nShort pitch:\n").append(project.getShortDescription()).append('\n');
        sb.append("\nFull story:\n").append(project.getFullDescription());
        return sb.toString();
    }

    /** Queue payload — public+static so AiJobWorker can deserialize
     *  into it directly, same as EmailServiceImpl's nested payload records. */
    public record FraudScanPayload(Long projectId) {}

    // ═════════════════════════════════════════════════════════════════════
    // Feature #44 — KYC Document Validation (vision)
    // ═════════════════════════════════════════════════════════════════════
    // Async, same reasoning and shape as #43: queueKycScan() is public (on
    // AiService), scanKycDocument() is what AiJobWorker actually calls and
    // is deliberately not on the interface.
    //
    // Scope is intentionally narrow: readability and OBVIOUS tampering only
    // -- this is a pre-check for a human admin, never a replacement for one,
    // and never changes KYC status by itself. It does not attempt identity
    // verification (matching the number/name against the image), which is a
    // meaningfully higher-stakes claim an LLM is not reliable enough to make
    // -- KycDocument.panNumber/aadhaarNumber are already collected as typed
    // text separately and aren't touched here.
    //
    // Privacy: the system prompt explicitly instructs the model not to
    // transcribe the ID number, name, address, or DOB into its response,
    // even though it can see them in the image -- there's no reason to give
    // this app's own database (or its logs) a second copy of that data. The
    // result is also admin-only: KycServiceImpl only attaches AI-check
    // fields to the admin queue response (getPendingKyc), never to the
    // creator-facing one (getMyKycStatus) -- letting a submitter see exactly
    // what the tampering check flagged would just teach them how to get a
    // fake past it next time.

    private static final String KYC_SYSTEM_PROMPT = """
            You are a document quality and authenticity pre-check for CrowdSpark, a crowdfunding \
            platform for creators in India. You are reviewing identity documents (PAN card and/or \
            Aadhaar card images) a creator submitted for KYC verification, before a human reviewer \
            looks at them.

            Your job is narrow: flag readability problems and obvious visual signs of tampering. You \
            are not making the final verification decision - a human always reviews this after you.

            Check for:
            - Readability: is the image in focus, well lit, not cropped or cut off, with the text and \
              photo clearly visible?
            - Obvious tampering: visually obvious signs of digital editing, such as mismatched fonts, \
              inconsistent lighting or shadows between the photo and the card, visible copy-paste \
              artifacts, blurred or smudged areas over specific fields, or text that does not align \
              with the card's printed layout.

            Important: do not transcribe or repeat the ID number, full name, address, or date of \
            birth visible on the document anywhere in your response. Only describe quality and \
            authenticity signals, never the personal data itself. If you cannot see the image clearly \
            enough to assess it, say so rather than guessing.

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no \
            text outside the JSON. It must have exactly these keys: readable (true or false), \
            tamperingSuspected (true or false), concerns (an array of 0-5 short strings describing \
            specific quality or authenticity issues, without quoting any personal data from the \
            document), and summary (one or two plain-text sentences for the admin reviewing this).""";

    private static final String KYC_USER_PROMPT =
            "Review the attached identity document image(s) for readability and obvious tampering "
                    + "signs, following the system instructions exactly. Do not transcribe any personal data.";

    @Override
    public void queueKycScan(Long kycDocumentId) {
        queueService.enqueue(AI_JOBS_QUEUE, "SCAN_KYC_DOCUMENT", new KycScanPayload(kycDocumentId),
                () -> scanKycDocument(kycDocumentId));
    }

    /** Called by AiJobWorker. Public (not on AiService) so the worker can
     *  reach it directly, same pattern as scanProjectForFraud(). */
    @Transactional
    public void scanKycDocument(Long kycDocumentId) {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("KYC scan requested for doc {} but GROQ_API_KEY is not configured — skipping", kycDocumentId);
            return; // background job — nothing is waiting on a response, so just skip rather than throw
        }

        KycDocument kyc = kycDocumentRepository.findById(kycDocumentId).orElse(null);
        if (kyc == null) {
            log.warn("KYC scan requested for doc {} but it no longer exists — skipping", kycDocumentId);
            return;
        }

        List<String> images = new ArrayList<>();
        if (kyc.getPanCardImageUrl() != null && !kyc.getPanCardImageUrl().isBlank()) {
            images.add(kyc.getPanCardImageUrl());
        }
        if (kyc.getAadhaarFrontImageUrl() != null && !kyc.getAadhaarFrontImageUrl().isBlank()) {
            images.add(kyc.getAadhaarFrontImageUrl());
        }
        if (kyc.getAadhaarBackImageUrl() != null && !kyc.getAadhaarBackImageUrl().isBlank()) {
            images.add(kyc.getAadhaarBackImageUrl());
        }

        KycDocumentAiCheck check = kycDocumentAiCheckRepository.findByKycDocument_Id(kycDocumentId)
                .orElseGet(() -> {
                    KycDocumentAiCheck c = new KycDocumentAiCheck();
                    c.setKycDocument(kyc);
                    return c;
                });

        if (images.isEmpty()) {
            log.warn("KYC scan requested for doc {} but no document images are attached — skipping", kycDocumentId);
            check.setStatus(KycCheckStatus.FAILED);
            check.setSummary("No document images were attached to scan.");
            check.setCheckedAt(LocalDateTime.now());
            kycDocumentAiCheckRepository.save(check);
            return;
        }

        try {
            // Qwen3.6-27B currently caps requests at 3 images -- PAN + Aadhaar
            // front + Aadhaar back is exactly the maximum this app ever sends.
            String   raw  = callGroqVision(KYC_SYSTEM_PROMPT, KYC_USER_PROMPT, images);
            JsonNode json = parseJson(raw);

            boolean readable   = json.path("readable").asBoolean(true);
            boolean tampering  = json.path("tamperingSuspected").asBoolean(false);
            String  summary    = trim(json.path("summary").asText(""), 500);

            List<String> concernsList = new ArrayList<>();
            if (json.path("concerns").isArray()) {
                for (JsonNode c : json.path("concerns")) {
                    String concern = trim(c.asText(""), 200);
                    if (!concern.isBlank()) concernsList.add(concern);
                }
            }

            check.setStatus(KycCheckStatus.COMPLETED);
            check.setReadable(readable);
            check.setTamperingSuspected(tampering);
            check.setConcerns(String.join("\n", concernsList));
            check.setSummary(summary.isBlank() ? "No specific concerns noted." : summary);
            check.setModel(visionModel);
            check.setCheckedAt(LocalDateTime.now());

        } catch (Exception e) {
            // Vision model down/rate-limited/unparseable, etc. — record
            // FAILED so the admin UI shows "scan unavailable" honestly
            // instead of a wrong result, and so this isn't retried forever.
            log.error("KYC scan failed for doc {}: {}", kycDocumentId, e.getMessage());
            check.setStatus(KycCheckStatus.FAILED);
            check.setCheckedAt(LocalDateTime.now());
        }

        kycDocumentAiCheckRepository.save(check);
    }

    /** Vision call — deliberately a separate method from callGroq()/
     *  callGroqRaw() rather than a third overload of the same helper.
     *  Those build { role, content: "<string>" } messages; a vision message
     *  needs content to be an array of typed parts ({type:"text",...} and
     *  {type:"image_url",...}), a genuinely different JSON shape, not just a
     *  different parameter list. Keeping it separate avoids reshaping the
     *  three proven, already-shipped text call sites (#39, #41, #42) to
     *  accommodate a shape only this one feature needs. */
    private String callGroqVision(String systemPrompt, String textPrompt, List<String> imageUrls) {
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", textPrompt);

        List<Map<String, Object>> userContent = new ArrayList<>();
        userContent.add(textPart);
        for (String url : imageUrls) {
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", Map.of("url", url));
            userContent.add(imagePart);
        }

        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);

        Map<String, Object> body = new HashMap<>();
        body.put("model", visionModel);
        body.put("max_completion_tokens", 800); // short structured JSON out, no need for the full text budget
        body.put("temperature", 0.2);            // low — this is a factual quality check, not creative writing
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(systemMsg, userMsg));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.exchange(groqUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Groq vision API error: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI document check failed. Please try again.");
        } catch (ResourceAccessException e) {
            log.error("Groq vision API timeout/network error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI document check timed out. Please try again.");
        }
        return extractText(resp.getBody());
    }

    /** Queue payload — public+static so AiJobWorker can deserialize into it
     *  directly, same as FraudScanPayload above. */
    public record KycScanPayload(Long kycDocumentId) {}

    // ═════════════════════════════════════════════════════════════════════
    // Feature #45 — Content Moderation
    // ═════════════════════════════════════════════════════════════════════
    // Covers two content types with one shared check (runModerationCheck
    // below) since the actual scan logic is identical either way — only the
    // text being checked, and what happens on a FLAGGED result, differ:
    //   - Projects: advisory only, same philosophy as #43. Projects already
    //     have a mandatory human approval gate before going live, so a flag
    //     here just adds a signal to that existing review, same admin/projects
    //     queue #43 already surfaces into.
    //   - Comments: no review gate exists before a comment is publicly
    //     visible, so a FLAGGED result auto-hides it (deleted=true, the same
    //     flag a real user/admin deletion already sets) pending a human
    //     decision in the new admin moderation queue. This is the one place
    //     in this feature that takes action on its own rather than staying
    //     purely advisory -- justified by there being no other gate to lean
    //     on the way projects and KYC both already have.

    private static final String MODERATION_SYSTEM_PROMPT = """
            You are a content moderator for CrowdSpark, a crowdfunding platform for creators in \
            India. You will be given a piece of user-submitted content - either a campaign \
            description or a comment - and asked to check it for policy violations.

            Flag content only for real, specific violations:
            - spam: repetitive junk, irrelevant links or advertising unrelated to the campaign, \
              gibberish, or content that exists purely to promote something else.
            - hate_speech: content that attacks, demeans, or incites hostility against people based \
              on protected characteristics such as race, religion, gender, caste, nationality, \
              sexual orientation, or disability.
            - misleading: false or unverifiable claims of guaranteed returns, fake credentials, fake \
              partnerships, or facts presented as certain that cannot possibly be known.

            Ordinary disagreement, criticism, negative feedback, or a poorly written but genuine \
            campaign are NOT violations. Only flag what the text actually contains - do not flag \
            something because it seems unusual or because you are uncertain, and never invent a \
            violation that is not actually present.

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no \
            text outside the JSON. It must have exactly these keys: flagged (true or false), \
            category (one of spam, hate_speech, misleading, or none), and reasoning (one or two \
            plain-text sentences explaining the decision either way).""";

    @Override
    public void queueProjectModerationScan(Long projectId) {
        queueService.enqueue(AI_JOBS_QUEUE, "SCAN_PROJECT_MODERATION", new ProjectModerationPayload(projectId),
                () -> scanProjectModeration(projectId));
    }

    @Override
    public void queueCommentModerationScan(Long commentId) {
        queueService.enqueue(AI_JOBS_QUEUE, "SCAN_COMMENT", new CommentModerationPayload(commentId),
                () -> scanCommentModeration(commentId));
    }

    /** Called by AiJobWorker. Public (not on AiService) so the worker can
     *  reach it directly, same pattern as scanProjectForFraud(). */
    @Transactional
    public void scanProjectModeration(Long projectId) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Moderation scan requested for project {} but GROQ_API_KEY is not configured — skipping", projectId);
            return;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            log.warn("Moderation scan requested for project {} but it no longer exists — skipping", projectId);
            return;
        }
        String text = "Content type: campaign description\n\nTitle: " + project.getTitle()
                + "\n\nShort pitch: " + project.getShortDescription()
                + "\n\nFull story:\n" + project.getFullDescription();

        // No onFlagged action — projects stay visible either way, pending
        // the human admin review they already require before going live.
        runModerationCheck(ContentType.PROJECT, projectId, text, null);
    }

    /** Called by AiJobWorker. Public (not on AiService), same as above. */
    @Transactional
    public void scanCommentModeration(Long commentId) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Moderation scan requested for comment {} but GROQ_API_KEY is not configured — skipping", commentId);
            return;
        }
        ProjectComment comment = projectCommentRepository.findById(commentId).orElse(null);
        if (comment == null || comment.isDeleted()) {
            log.warn("Moderation scan requested for comment {} but it no longer exists or is already deleted — skipping", commentId);
            return;
        }
        String text = "Content type: comment on a crowdfunding campaign\n\nComment text:\n" + comment.getContent();

        runModerationCheck(ContentType.COMMENT, commentId, text, () -> {
            comment.setDeleted(true);
            projectCommentRepository.save(comment);
            log.info("Comment {} auto-hidden by content moderation pending admin review", commentId);
        });
    }

    /** Shared by both content types above — builds/finds the check record,
     *  calls Groq, parses the verdict, and (only if flagged) runs the
     *  content-type-specific reaction passed in as onFlagged. */
    private void runModerationCheck(ContentType type, Long contentId, String textToCheck, Runnable onFlagged) {
        ContentModerationCheck check = contentModerationCheckRepository
                .findByContentTypeAndContentId(type, contentId)
                .orElseGet(() -> {
                    ContentModerationCheck c = new ContentModerationCheck();
                    c.setContentType(type);
                    c.setContentId(contentId);
                    return c;
                });

        try {
            String   raw  = callGroq(MODERATION_SYSTEM_PROMPT, textToCheck, 0.2);
            JsonNode json = parseJson(raw);

            boolean flagged   = json.path("flagged").asBoolean(false);
            String  category  = trim(json.path("category").asText("none"), 20).toLowerCase();
            String  reasoning = trim(json.path("reasoning").asText(""), 500);

            check.setStatus(flagged ? ModerationStatus.FLAGGED : ModerationStatus.CLEAR);
            check.setCategory(category);
            check.setReasoning(reasoning.isBlank() ? "No specific concerns noted." : reasoning);
            check.setModel(model);
            check.setCheckedAt(LocalDateTime.now());
            contentModerationCheckRepository.save(check);

            if (flagged && onFlagged != null) {
                onFlagged.run();
            }

        } catch (Exception e) {
            // Groq down, malformed response, etc. — record FAILED rather
            // than silently defaulting to CLEAR, so nothing gets waved
            // through just because the check itself broke.
            log.error("Content moderation scan failed for {} {}: {}", type, contentId, e.getMessage());
            check.setStatus(ModerationStatus.FAILED);
            check.setCheckedAt(LocalDateTime.now());
            contentModerationCheckRepository.save(check);
        }
    }

    /** Queue payloads — public+static so AiJobWorker can deserialize into
     *  them directly, same as FraudScanPayload/KycScanPayload above. */
    public record ProjectModerationPayload(Long projectId) {}
    public record CommentModerationPayload(Long commentId) {}

    // ═════════════════════════════════════════════════════════════════════
    // Feature #46 — Campaign Improvement Suggestions
    // ═════════════════════════════════════════════════════════════════════
    // Complements #41 rather than duplicating it: predictCampaignSuccess()
    // gives one holistic 0-100 score with a flat tip list; this gives
    // specific, categorized actions (title alternatives / reward-tier gaps
    // / media gaps). Different enough in shape and purpose to warrant its
    // own request/response DTOs rather than reusing CampaignScoreRequest —
    // in particular this one takes actual reward-tier data (title + amount),
    // not just a count, since a useful "gap in your price ladder" suggestion
    // needs the real numbers to reason about.

    private static final String SUGGESTIONS_SYSTEM_PROMPT = """
            You are a campaign coach for CrowdSpark, a crowdfunding platform for creators in India. \
            All amounts are in Indian Rupees (INR).

            You will be given a creator's in-progress campaign draft - title, pitch, story, funding \
            goal, media, and reward tiers - and asked for specific, structured suggestions to \
            strengthen it before launch, organized into three categories.

            For each category, give concrete suggestions grounded in what the creator has actually \
            written, not generic advice that could apply to any campaign:
            - Title: 2-3 alternative titles that are more specific or compelling than the current \
              one, each a genuine option, not a joke or filler. If the current title is already \
              strong, say so instead of forcing weaker alternatives.
            - Rewards: specific reward tier ideas - gaps in the price ladder, a missing low-cost \
              entry tier, a tier whose description could be clearer or more appealing. If there are \
              no reward tiers yet, suggest 2-3 concrete starting tiers with amounts appropriate to \
              the goal.
            - Media: what's actually missing - a video, more images, a specific kind of shot (product \
              photos, behind the scenes, the creator's own face) - based only on what's currently \
              attached.

            Only include a suggestion if it is genuinely useful - an empty list for a category is \
            correct when there is nothing worth changing. Never invent facts about the campaign that \
            were not given.

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no \
            text outside the JSON. It must have exactly these keys: titleSuggestions (array of 0-3 \
            short strings), rewardSuggestions (array of 0-4 short strings), mediaSuggestions (array \
            of 0-3 short strings), and overallNote (one short plain-text sentence, or an empty string \
            if the per-category suggestions already say everything worth saying).""";

    @Override
    public CampaignSuggestionsResponse getCampaignSuggestions(CampaignSuggestionsRequest request, Long creatorId) {

        requireApiKey();
        enforceDailyLimit("suggestions", creatorId, suggestionsDailyLimit);

        String   raw  = callGroq(SUGGESTIONS_SYSTEM_PROMPT, buildSuggestionsPrompt(request), 0.5);
        JsonNode json = parseJson(raw);

        List<String> titleSuggestions  = extractStringArray(json, "titleSuggestions", 3, 200);
        List<String> rewardSuggestions = extractStringArray(json, "rewardSuggestions", 4, 250);
        List<String> mediaSuggestions  = extractStringArray(json, "mediaSuggestions", 3, 200);
        String       overallNote       = trim(json.path("overallNote").asText(""), 300);

        return CampaignSuggestionsResponse.builder()
                .titleSuggestions(titleSuggestions)
                .rewardSuggestions(rewardSuggestions)
                .mediaSuggestions(mediaSuggestions)
                .overallNote(overallNote)
                .model(model)
                .build();
    }

    private String buildSuggestionsPrompt(CampaignSuggestionsRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(req.getTitle()).append('\n');
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            sb.append("Category: ").append(req.getCategory()).append('\n');
        }
        sb.append("Funding goal: INR ").append(String.format("%,.0f", req.getGoalAmount())).append('\n');
        sb.append("Thumbnail uploaded: ").append(req.isHasThumbnail() ? "yes" : "no").append('\n');
        sb.append("Video uploaded: ").append(req.isHasVideo() ? "yes" : "no").append('\n');
        sb.append("Total media files: ").append(req.getMediaCount()).append('\n');

        List<CampaignSuggestionsRequest.RewardTierSummary> tiers = req.getRewardTiers();
        if (tiers == null || tiers.isEmpty()) {
            sb.append("Reward tiers: none set up yet\n");
        } else {
            sb.append("Reward tiers:\n");
            for (CampaignSuggestionsRequest.RewardTierSummary t : tiers) {
                sb.append("- INR ").append(String.format("%,.0f", t.getMinimumAmount()))
                  .append(": ").append(t.getTitle());
                if (t.getDescription() != null && !t.getDescription().isBlank()) {
                    sb.append(" — ").append(t.getDescription());
                }
                sb.append('\n');
            }
        }

        sb.append("\nShort pitch:\n").append(req.getShortDescription()).append('\n');
        sb.append("\nFull story:\n").append(req.getFullDescription());
        return sb.toString();
    }

    /** Reads a JSON array field into a capped list of trimmed strings,
     *  dropping any blank entries. Shared by every feature in this file
     *  that returns a suggestions/tips list (#41's tips, #46's three
     *  categories) — pulled out here now that a second feature needs the
     *  exact same array-reading loop #41 already had inline. */
    private List<String> extractStringArray(JsonNode parent, String field, int maxItems, int maxLenEach) {
        List<String> out = new ArrayList<>();
        if (parent.path(field).isArray()) {
            for (JsonNode item : parent.path(field)) {
                String s = trim(item.asText(""), maxLenEach);
                if (!s.isBlank()) out.add(s);
                if (out.size() >= maxItems) break;
            }
        }
        return out;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Feature #47 — Auto-Tagging & Category Detection
    // ═════════════════════════════════════════════════════════════════════
    // Meant for Step 1 of the wizard: title + short pitch are both already
    // on that step, so a suggestion can run before the creator has written
    // the full story or touched any other step. The model is given the
    // platform's REAL category list (id + name) and told to only pick from
    // it; the response is re-validated against that same list server-side
    // before being returned, so a hallucinated or stale id can never reach
    // the frontend even if the model ignores the instruction.

    private static final String CATEGORY_SYSTEM_PROMPT = """
            You are a category classifier for CrowdSpark, a crowdfunding platform for creators in \
            India. You will be given a campaign title and short pitch, plus the platform's list of \
            available categories, each with an id and a name.

            Pick the categories that genuinely fit this campaign - usually just one, occasionally two \
            if the campaign clearly spans two areas. Only choose ids that appear in the list given - \
            never invent a category or id that is not there. If nothing fits well, it is fine to \
            return an empty list rather than forcing a weak match.

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no \
            text outside the JSON. It must have exactly these keys: categoryIds (an array of 0-2 \
            numbers, each matching an id from the list given), and reasoning (one short plain-text \
            sentence explaining the pick).""";

    @Override
    public CategorySuggestionResponse suggestCategories(CategorySuggestionRequest request, Long creatorId) {

        requireApiKey();
        enforceDailyLimit("category-suggestion", creatorId, categorySuggestionDailyLimit);

        List<Category> allCategories = categoryRepository.findAll();
        if (allCategories.isEmpty()) {
            return CategorySuggestionResponse.builder()
                    .categoryIds(List.of())
                    .reasoning("No categories are configured on this platform yet.")
                    .build();
        }

        String   raw  = callGroq(CATEGORY_SYSTEM_PROMPT, buildCategoryPrompt(request, allCategories), 0.2);
        JsonNode json = parseJson(raw);

        Set<Long> validIds = allCategories.stream().map(Category::getId).collect(Collectors.toSet());
        List<Long> picked = new ArrayList<>();
        if (json.path("categoryIds").isArray()) {
            for (JsonNode idNode : json.path("categoryIds")) {
                long id = idNode.asLong(-1);
                if (validIds.contains(id) && !picked.contains(id)) {
                    picked.add(id);
                }
                if (picked.size() >= 2) break;
            }
        }
        String reasoning = trim(json.path("reasoning").asText(""), 200);

        return CategorySuggestionResponse.builder()
                .categoryIds(picked)
                .reasoning(reasoning.isBlank() ? "Based on the title and pitch." : reasoning)
                .build();
    }

    private String buildCategoryPrompt(CategorySuggestionRequest req, List<Category> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("Campaign title: ").append(req.getTitle()).append('\n');
        sb.append("Short pitch: ").append(req.getShortDescription()).append('\n');
        sb.append("\nAvailable categories:\n");
        for (Category c : categories) {
            sb.append("id ").append(c.getId()).append(": ").append(c.getName()).append('\n');
        }
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Feature #48 — Creator Analytics Insights (natural language)
    // ═════════════════════════════════════════════════════════════════════
    // The only AI feature in this file called from a @Scheduled job
    // (CreatorInsightSchedulerService, new in this feature) instead of a
    // live HTTP request or an async queue — it's already running inside a
    // weekly batch job, so there's nothing further to make async. Every
    // number in the prompt is computed by the caller from real data before
    // this method ever runs; this only turns those numbers into 2-4
    // sentences of prose, it never invents or looks up anything itself.

    private static final String WEEKLY_INSIGHT_SYSTEM_PROMPT = """
            You are writing a short, friendly weekly performance update for a creator on CrowdSpark, \
            a crowdfunding platform in India. All amounts are in Indian Rupees (INR). You will be \
            given this week's numbers for one of their live campaigns.

            Write 2 to 4 sentences summarizing how the week went, in a natural, encouraging tone - not \
            corporate, not over the top. Mention the most relevant number or trend (funding progress, \
            new backers, views), and if something is worth their attention (funding has stalled, the \
            deadline is close), say so plainly and kindly rather than glossing over it. Never invent a \
            number or fact you were not given.

            Respond with ONLY a single valid JSON object - no markdown code fences, no preamble, no \
            text outside the JSON. It must have exactly one key: summary, a short plain-text paragraph \
            (2-4 sentences, no markdown).""";

    @Override
    public String generateWeeklyInsightText(String projectTitle, double currentAmount, double goalAmount,
                                             long daysLeft, long viewsThisWeek,
                                             long newBackersThisWeek, long newBackersLastWeek) {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Weekly insight requested for \"{}\" but GROQ_API_KEY is not configured — skipping", projectTitle);
            return null; // scheduler skips the email/row for this project rather than storing a placeholder
        }

        int fundedPercent = goalAmount > 0 ? (int) Math.round((currentAmount / goalAmount) * 100) : 0;
        String trend = newBackersThisWeek > newBackersLastWeek ? "up from"
                : newBackersThisWeek < newBackersLastWeek ? "down from" : "the same as";

        String userPrompt = "Campaign: " + projectTitle + '\n'
                + "Raised so far: INR " + String.format("%,.0f", currentAmount)
                + " of INR " + String.format("%,.0f", goalAmount) + " (" + fundedPercent + "%)\n"
                + "Days left: " + daysLeft + '\n'
                + "Views this week: " + viewsThisWeek + '\n'
                + "New backers this week: " + newBackersThisWeek + " (" + trend + " last week's "
                + newBackersLastWeek + ")";

        try {
            String   raw  = callGroq(WEEKLY_INSIGHT_SYSTEM_PROMPT, userPrompt, 0.6);
            JsonNode json = parseJson(raw);
            String   summary = trim(json.path("summary").asText(""), 600);
            return summary.isBlank() ? null : summary;
        } catch (Exception e) {
            // Scheduler catches per-project, same as DeadlineSchedulerService's
            // per-item try/catch — one failed summary shouldn't stop the rest
            // of the week's batch from running.
            log.error("Weekly insight generation failed for \"{}\": {}", projectTitle, e.getMessage());
            return null;
        }
    }

    @Override
    public String getConfiguredModel() {
        return model;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Shared: Groq HTTP call, JSON parsing, small helpers
    // ═════════════════════════════════════════════════════════════════════

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI feature requested but GROQ_API_KEY is not configured");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI features aren't configured on this server yet. Please try again later.");
        }
    }

    /** Groq/OpenAI-compatible Chat Completions call. Returns the assistant's
     *  raw text content (expected to be a JSON string per every system
     *  prompt in this file -- response_format=json_object guarantees
     *  syntactically valid JSON, not that it matches our schema, hence
     *  parseJson()'s defensive field-by-field reads downstream). */
    private String callGroq(String systemPrompt, String userPrompt, double temp) {
        return callGroqRaw(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ), temp);
    }

    /** Multi-turn variant for Feature #42's chatbot -- same call, just with
     *  a full conversation instead of one user turn. */
    private String callGroq(String systemPrompt, List<Map<String, String>> conversation, double temp) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(conversation);
        return callGroqRaw(messages, temp);
    }

    private String callGroqRaw(List<Map<String, String>> messages, double temp) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_completion_tokens", maxTokens); // Groq's param name -- NOT max_tokens
        body.put("temperature", temp);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey); // Groq uses standard "Authorization: Bearer <key>"

        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.exchange(groqUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Groq API error: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI generation failed. Please try again in a moment.");
        } catch (ResourceAccessException e) {
            log.error("Groq API timeout/network error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "AI generation timed out. Please try again.");
        }
        return extractText(resp.getBody());
    }

    // Groq/OpenAI shape: { choices: [ { message: { role, content } } ] }
    @SuppressWarnings("unchecked")
    private String extractText(Map body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from AI service.");
        }
        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            log.error("Groq response had no choices: {}", body);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned no content.");
        }
        Object first = choices.get(0);
        if (first instanceof Map<?, ?> choice
                && choice.get("message") instanceof Map<?, ?> message
                && message.get("content") instanceof String text) {
            return text;
        }
        log.error("Groq response's first choice had no message content: {}", first);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service returned an unexpected response format.");
    }

    private JsonNode parseJson(String raw) {
        String cleaned = raw.trim();
        // Defensive: strip ```json ... ``` fences in case the model adds them
        // despite instructions -- and response_format=json_object -- not to.
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(json)?", "").trim();
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }
        try {
            return mapper.readTree(cleaned);
        } catch (Exception e) {
            log.error("Failed to parse AI JSON response: {}", raw, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI returned a response we couldn't understand. Please try again.");
        }
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "\u2026";
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ── Per-creator daily rate limits, one counter per manually-triggered
    // feature (#40's recommendations are protected by its own 6h cache
    // instead, since it's fetched passively rather than triggered by a
    // repeated manual action) ───────────────────────────────────────────────

    private void enforceDailyLimit(String featureKey, Long creatorId, int limit) {
        String key = "ai:" + featureKey + ":" + creatorId + ":" + LocalDate.now().format(DAY_KEY);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, 26, TimeUnit.HOURS);
            }
            if (count != null && count > limit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "You've reached today's limit of " + limit + " for this AI feature. Try again tomorrow.");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // Redis down — fail open, matching RateLimitFilter's convention
            log.error("AI rate-limit check failed (Redis error), allowing request: {}", e.getMessage());
        }
    }
}
