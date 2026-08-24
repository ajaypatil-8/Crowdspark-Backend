// src/main/java/Crowdspark/Crowdspark/service/impl/AiServiceImpl.java
// Feature #39 — AI Campaign Description Generator
// Feature #40 — AI-Powered Project Recommendations
//
// Both features share one Groq (OpenAI-compatible, free tier) client -- see
// callGroq() below, extracted here now that a second feature needs the same
// "build messages -> POST -> extract text" plumbing #39 already had.
//
// GROQ_API_KEY is intentionally OPTIONAL at startup (mirrors how
// FirebaseConfig handles a missing FIREBASE_SERVICE_ACCOUNT_PATH): this app
// is already live on Render, and a hard-required @NotBlank in
// AppSecretsProperties would crash the entire backend -- every unrelated
// endpoint included -- the moment either feature ships, if the key hasn't
// been added on Render yet. Instead, both features degrade to a clear 503
// until the key is set.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.CampaignScoreRequest;
import Crowdspark.Crowdspark.dto.CampaignScoreResponse;
import Crowdspark.Crowdspark.dto.GenerateDescriptionRequest;
import Crowdspark.Crowdspark.dto.GenerateDescriptionResponse;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.dto.RecommendationsResponse;
import Crowdspark.Crowdspark.dto.RecommendedProjectResponse;
import Crowdspark.Crowdspark.entity.Category;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.SavedProject;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
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

    private final RestTemplate           restTemplate;
    private final StringRedisTemplate    redisTemplate;
    private final ProjectRepository      projectRepository;
    private final DonationRepository     donationRepository;
    private final SavedProjectRepository savedProjectRepository;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Value("${groq.api-key:}")
    private String apiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

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

        List<String> tips = new ArrayList<>();
        if (json.path("tips").isArray()) {
            for (JsonNode t : json.path("tips")) {
                String tip = trim(t.asText(""), 200);
                if (!tip.isBlank()) tips.add(tip);
                if (tips.size() >= 6) break;
            }
        }

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
     *  raw text content (expected to be a JSON string per both system prompts
     *  above -- response_format=json_object guarantees syntactically valid
     *  JSON, not that it matches our schema, hence parseJson()'s defensive
     *  field-by-field reads downstream). */
    private String callGroq(String systemPrompt, String userPrompt, double temp) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_completion_tokens", maxTokens); // Groq's param name -- NOT max_tokens
        body.put("temperature", temp);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

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
