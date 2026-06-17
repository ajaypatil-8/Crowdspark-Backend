// src/main/java/Crowdspark/Crowdspark/controller/FundingStreamController.java
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.service.FundingStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Funding Stream", description = "Real-time funding updates via Server-Sent Events")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class FundingStreamController {

    private final FundingStreamService fundingStreamService;

    /**
     * GET /api/projects/{id}/funding-stream
     *
     * Opens a Server-Sent Events stream for a project's funding progress.
     * The browser connects once and receives push updates in real time
     * whenever a new donation is confirmed.
     *
     * Events emitted:
     *   name: "funding-update"
     *   data: { projectId, currentAmount, goalAmount,
     *            fundedPercentage, backersCount, status, timestamp }
     *
     * The first event is sent immediately with the current snapshot.
     * Subsequent events fire only when a new payment is verified.
     * A ":heartbeat" comment is sent every 30s to keep the connection alive.
     *
     * Public — no auth required. Anyone viewing a project page can subscribe.
     */
    @Operation(
        summary = "Subscribe to real-time funding updates",
        description = "SSE stream. Connect once, receive push events on every new donation."
    )
    @GetMapping(value = "/{projectId}/funding-stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter fundingStream(@PathVariable Long projectId) {
        return fundingStreamService.subscribe(projectId);
    }
}
