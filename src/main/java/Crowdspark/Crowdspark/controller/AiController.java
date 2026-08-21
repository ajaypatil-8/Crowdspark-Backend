// src/main/java/Crowdspark/Crowdspark/controller/AiController.java
// Feature #39 — AI Campaign Description Generator
// Home for all Claude-powered creator tools (#39-#48 will add siblings here).

package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.GenerateDescriptionRequest;
import Crowdspark.Crowdspark.dto.GenerateDescriptionResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Tools", description = "Claude-powered creator assistance")
public class AiController {

    private final AiService  aiService;
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
}
