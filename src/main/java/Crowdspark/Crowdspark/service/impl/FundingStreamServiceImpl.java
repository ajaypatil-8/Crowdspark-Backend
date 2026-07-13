// src/main/java/Crowdspark/Crowdspark/service/impl/FundingStreamServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.FundingUpdateDto;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.service.FundingStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingStreamServiceImpl implements FundingStreamService {

    private final ProjectRepository  projectRepository;
    private final DonationRepository donationRepository;

    /**
     * Active emitters: projectId → list of SSE clients watching that project.
     * ConcurrentHashMap + CopyOnWriteArrayList = thread-safe without explicit locks.
     */
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // ── subscribe ─────────────────────────────────────────────────────────────

    @Override
    public SseEmitter subscribe(Long projectId) {
        // Validate project exists
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }

        // 5-minute timeout — browser reconnects automatically (SSE spec)
        SseEmitter emitter = new SseEmitter(300_000L);

        // Register the emitter
        emitters.computeIfAbsent(projectId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Send initial snapshot immediately so the client has data right away
        try {
            FundingUpdateDto snapshot = buildSnapshot(projectId);
            emitter.send(SseEmitter.event()
                    .name("funding-update")
                    .data(snapshot));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // Cleanup callbacks — remove emitter when connection closes
        Runnable cleanup = () -> removeEmitter(projectId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(err -> cleanup.run());

        log.debug("SSE client subscribed: project={} activeClients={}",
                projectId, emitters.getOrDefault(projectId, List.of()).size());

        return emitter;
    }

    // ── broadcast ─────────────────────────────────────────────────────────────

    @Override
    public void broadcast(Long projectId, FundingUpdateDto update) {
        List<SseEmitter> projectEmitters = emitters.get(projectId);
        if (projectEmitters == null || projectEmitters.isEmpty()) {
            return; // No one watching this project right now
        }

        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter emitter : projectEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("funding-update")
                        .data(update));
            } catch (IOException e) {
                // Client disconnected — mark for removal
                dead.add(emitter);
            }
        }

        dead.forEach(e -> removeEmitter(projectId, e));

        if (!dead.isEmpty()) {
            log.debug("SSE broadcast: project={} sent={} dead={}",
                    projectId, projectEmitters.size(), dead.size());
        }
    }

    // ── buildSnapshot ─────────────────────────────────────────────────────────

    @Override
    public FundingUpdateDto buildSnapshot(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found"));

        long backers = donationRepository.countByProject_IdAndPaymentStatus(
                projectId, PaymentStatus.SUCCESS);

        int pct = project.getGoalAmount() > 0
                ? (int) ((project.getCurrentAmount() / project.getGoalAmount()) * 100)
                : 0;

        return FundingUpdateDto.builder()
                .projectId(projectId)
                .currentAmount(project.getCurrentAmount())
                .goalAmount(project.getGoalAmount())
                .fundedPercentage(pct)
                .backersCount(backers)
                .status(project.getStatus().name())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ── heartbeat ─────────────────────────────────────────────────────────────

    /**
     * Sends a keep-alive comment (":heartbeat") every 30 seconds to all
     * active connections. Without this, proxies/load balancers close idle connections.
     */
    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeats() {
        if (emitters.isEmpty()) return;

        emitters.forEach((projectId, projectEmitters) -> {
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : projectEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    dead.add(emitter);
                }
            }
            dead.forEach(e -> removeEmitter(projectId, e));
        });
    }

    // ── private helpers ───────────────────────────────────────────────────────

    // FIX #15: was list = emitters.get(projectId); ...; emitters.remove(projectId) as
    // separate steps. If a new client subscribed to the same project in the gap
    // between this thread's list.isEmpty() check and its emitters.remove(projectId)
    // call, that new subscriber's emitter got added to the (about-to-be-deleted) list
    // and then silently vanished with it — they'd get their initial snapshot but never
    // another broadcast again, with no error anywhere. computeIfPresent makes the
    // whole read-modify-write atomic per key, closing that window.
    private void removeEmitter(Long projectId, SseEmitter emitter) {
        emitters.computeIfPresent(projectId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}