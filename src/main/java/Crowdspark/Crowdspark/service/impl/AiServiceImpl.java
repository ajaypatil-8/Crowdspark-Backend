// src/main/java/Crowdspark/Crowdspark/service/impl/AiServiceImpl.java
// Feature #39 — AI Campaign Description Generator
//
// REBUILT to call Groq's OpenAI-compatible Chat Completions API instead of
// Anthropic. Groq's developer tier is genuinely free -- no credit card, no
// trial-credit countdown -- just request-rate limits. RestTemplate usage
// matches the convention PayoutServiceImpl uses for Razorpay. No new DB
// table -- this is a stateless draft-and-review tool; whatever the creator
// accepts gets saved later through the existing project-creation flow, not
// by this service.
//
// GROQ_API_KEY is intentionally OPTIONAL at startup (mirrors how
// FirebaseConfig handles a missing FIREBASE_SERVICE_ACCOUNT_PATH): this app
// is already live on Render, and a hard-required @NotBlank in
// AppSecretsProperties would crash the entire backend -- every unrelated
// endpoint included -- the moment this change ships, if the key hasn't been
// added on Render yet. Instead, this one feature degrades to a clear 503
// until the key is set. That reasoning has nothing to do with cost -- it'd
// apply the same way even though Groq is free.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.GenerateDescriptionRequest;
import Crowdspark.Crowdspark.dto.GenerateDescriptionResponse;
import Crowdspark.Crowdspark.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final RestTemplate        restTemplate;
    private final StringRedisTemplate redisTemplate;

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

    private static final DateTimeFormatter DAY_KEY  = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final double            MIN_GOAL = 1_000;
    private static final double            MAX_GOAL = 100_000_000;

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

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI description generation requested but GROQ_API_KEY is not configured");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI drafting isn't configured on this server yet. Please try again later.");
        }

        enforceDailyLimit(creatorId);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_completion_tokens", maxTokens); // Groq's param name -- NOT max_tokens
        body.put("temperature", temperature);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", buildUserPrompt(request))
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

        String   rawText = extractText(resp.getBody());
        JsonNode json     = parseJson(rawText);

        String shortPitch      = trim(json.path("shortPitch").asText(""), 300);
        String fullDescription = json.path("fullDescription").asText("");
        String goalReasoning   = trim(json.path("goalReasoning").asText(""), 400);
        double suggestedGoal   = clamp(json.path("suggestedGoalAmount").asDouble(50_000), MIN_GOAL, MAX_GOAL);

        if (shortPitch.isBlank() || fullDescription.isBlank()) {
            log.error("Groq returned unparseable/incomplete content: {}", rawText);
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

    // ── Prompt building ─────────────────────────────────────────────────────
    // Unchanged by the provider swap -- the prompt content doesn't care which
    // model reads it, only the transport around it changed.

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

    // ── Response parsing ────────────────────────────────────────────────────
    // Groq/OpenAI shape: { choices: [ { message: { role, content } } ] }
    // (Anthropic's shape was { content: [ { type, text } ] } -- this method
    // is the part that actually had to change for the swap.)

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

    // ── Per-creator daily rate limit ────────────────────────────────────────
    // Groq's free tier is request-rate-limited at the org level (shared
    // across every creator using this app), not billed per token -- so this
    // cap exists to keep one creator from burning through that shared quota
    // and to stop a scripted/abuse loop, not to protect a bill. Raised from
    // 15 to 25 vs. the Anthropic version since there's no per-call cost to
    // weigh against generosity anymore.

    private void enforceDailyLimit(Long creatorId) {
        String key = "ai:desc-gen:" + creatorId + ":" + LocalDate.now().format(DAY_KEY);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, 26, TimeUnit.HOURS);
            }
            if (count != null && count > dailyLimit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "You've reached today's limit of " + dailyLimit + " AI generations. Try again tomorrow.");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // Redis down — fail open, matching RateLimitFilter's convention
            log.error("AI rate-limit check failed (Redis error), allowing request: {}", e.getMessage());
        }
    }
}
