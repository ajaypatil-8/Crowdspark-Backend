// src/main/java/Crowdspark/Crowdspark/service/FundingStreamService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.FundingUpdateDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface FundingStreamService {

    /**
     * Registers a new SSE client for a project's funding stream.
     * Sends an initial snapshot immediately, then waits for broadcasts.
     */
    SseEmitter subscribe(Long projectId);

    /**
     * Broadcasts a funding update to all clients watching this project.
     * Called by PaymentServiceImpl after a donation is confirmed,
     * and by DeadlineSchedulerService when status changes.
     */
    void broadcast(Long projectId, FundingUpdateDto update);

    /** Builds the current funding snapshot for a project */
    FundingUpdateDto buildSnapshot(Long projectId);
}
