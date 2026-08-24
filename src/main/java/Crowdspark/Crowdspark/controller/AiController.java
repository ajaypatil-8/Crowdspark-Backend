// src/main/java/Crowdspark/Crowdspark/controller/AiController.java
// Feature #39 — AI Campaign Description Generator
// Feature #40 — AI-Powered Project Recommendations
// Feature #41 — AI Campaign Success Predictor
// Feature #42 — AI Support Chatbot
// Home for all Groq-powered creator/backer tools (#43-#48 will add siblings
// here too).

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.CampaignScoreRequest;
import Crowdspark.Crowdspark.dto.CampaignScoreResponse;
import Crowdspark.Crowdspark.dto.GenerateDescriptionRequest;
import Crowdspark.Crowdspark.dto.GenerateDescriptionResponse;
import Crowdspark.Crowdspark.dto.RecommendationsResponse;
import Crowdspark.Crowdspark.dto.SupportChatRequest;
import Crowdspark.Crowdspark.dto.SupportChatResponse;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.service.AiService;
import Crowdspark.Crowdspark.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Tools", description = "Groq-powered creator and backer assistance")
public class AiController {

    private final AiService   aiService;
    private final UserService userService;

    /**
     * POST /api/v1/ai/campaign-description
     * Creator only — title + rough bullet points in, a drafted short pitch,
     * full Markdown story, and a suggested funding goal out. Nothing is
     * persisted here; the creator applies the draft into the campaign wizard
     * and it's saved when they submit the campaign as normal.
     */
    @Operation(summary = "Generate a campaign description with AI",
            description = "Creator only. Turns a title + rough bullet points into a short pitch, "
                    + "full Markdown story, and a suggested funding goal.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/campaign-description")
    public ResponseEntity<ApiResponse<GenerateDescriptionResponse>> generateDescription(
            @Valid @RequestBody GenerateDescriptionRequest request,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        GenerateDescriptionResponse response =
                aiService.generateCampaignDescription(request, creator.getId());
        return ResponseEntity.ok(ApiResponse.ok("Draft generated", response));
    }

    /**
     * GET /api/v1/ai/recommendations
     * Any authenticated user — up to 6 live campaigns picked and explained
     * for this specific backer, based on what they've backed, saved,
     * declared interest in, and recently viewed. Falls back to trending
     * picks (personalized=false in the response) when there's no signal yet.
     */
    @Operation(summary = "Get AI-picked project recommendations",
            description = "Any authenticated user. Ranks live campaigns using backed/interested "
                    + "categories and recent views; falls back to trending picks when there's no signal.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<RecommendationsResponse>> getRecommendations(
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        return ResponseEntity.ok(ApiResponse.ok(aiService.getRecommendations(user)));
    }

    /**
     * POST /api/v1/ai/recently-viewed/{projectId}
     * Any authenticated user — fire and forget, called by the frontend on
     * project detail page load (alongside, not instead of, the existing
     * anonymous analytics view tracker). Feeds getRecommendations() above.
     */
    @Operation(summary = "Track a project view for personalization",
            description = "Called once per page load when the viewer is logged in. Async — returns immediately.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/recently-viewed/{projectId}")
    public ResponseEntity<Void> trackRecentlyViewed(
            @PathVariable Long projectId,
            @AuthenticationPrincipal String username) {

        User user = userService.getByUsername(username);
        aiService.trackRecentlyViewed(user.getId(), projectId);
        return ResponseEntity.accepted().build();
    }

    /**
     * POST /api/v1/ai/success-score
     * Creator only — scores a draft campaign 0-100 on likely funding
     * success, with an honest explanation and improvement tips. Meant for
     * Step 5 (Review) of the campaign wizard, before submission. Stateless,
     * same as campaign-description — nothing here touches the database.
     */
    @Operation(summary = "Score a draft campaign's likely success",
            description = "Creator only. Scores 0-100 with an explanation and 3-5 improvement tips, "
                    + "based on the draft's story, goal, media, and reward tiers.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasRole('CREATOR')")
    @PostMapping("/success-score")
    public ResponseEntity<ApiResponse<CampaignScoreResponse>> predictSuccess(
            @Valid @RequestBody CampaignScoreRequest request,
            @AuthenticationPrincipal String username) {

        User creator = userService.getByUsername(username);
        CampaignScoreResponse response = aiService.predictCampaignSuccess(request, creator.getId());
        return ResponseEntity.ok(ApiResponse.ok("Score generated", response));
    }

    /**
     * POST /api/v1/ai/support-chat
     * Public — no login required, unlike every other endpoint in this
     * controller. Also added to SecurityConfig's permitAll list (omitting
     * @PreAuthorize alone is not enough here; the app's default is
     * .anyRequest().authenticated(), so this needs an explicit exception).
     * IP-rate-limited via RateLimitFilter instead of a per-user cap, since
     * anonymous callers have no user id to key on.
     */
    @Operation(summary = "Send a message to the support chatbot",
            description = "Public. Stateless — send the full conversation each turn, get the next "
                    + "assistant reply back. escalate=true means the frontend should offer a human.")
    @PostMapping("/support-chat")
    public ResponseEntity<ApiResponse<SupportChatResponse>> supportChat(
            @Valid @RequestBody SupportChatRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(aiService.handleSupportChat(request)));
    }
}
