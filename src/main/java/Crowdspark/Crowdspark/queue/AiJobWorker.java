// src/main/java/Crowdspark/Crowdspark/queue/AiJobWorker.java
// Feature #43 — AI Fraud & Risk Detection (introduced this worker)
// Feature #44 — AI KYC Document Validation (added a second job type)
// Feature #45 — AI Content Moderation (added two more — project + comment)
//
// Same structural pattern as EmailJobWorker: a small pool of daemon threads
// BRPOP-ing a Redis queue in a loop, dispatching by job type, dead-lettering
// anything that throws. This was FraudScanJobWorker (a single-purpose,
// single-job-type worker) until this feature needed the same async pattern
// for a second job type -- rather than standing up a near-identical second
// worker class, it was generalized here to dispatch on job type, exactly
// how EmailJobWorker already handles its own several email job types on one
// queue. If you deployed Feature #43 already, delete FraudScanJobWorker.java
// and add this file instead -- the queue name changed too (see
// AiServiceImpl.AI_JOBS_QUEUE).
//
// Depends on the CONCRETE AiServiceImpl class, not the AiService interface —
// scanProjectForFraud()/scanKycDocument() are deliberately not part of the
// public interface, exactly like EmailJobWorker's relationship to
// EmailServiceImpl's "...Now" methods.

package Crowdspark.Crowdspark.queue;

import Crowdspark.Crowdspark.service.impl.AiServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class AiJobWorker {

    private static final String QUEUE_NAME = "ai-jobs";
    private static final int WORKER_THREADS = 2; // two job types now sharing one queue; still low volume
    private static final int POLL_TIMEOUT_SECONDS = 5;

    private final RedisQueueService queueService;
    private final AiServiceImpl     aiServiceImpl;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private ExecutorService executor;
    private volatile boolean running = true;
    private final AtomicInteger threadCounter = new AtomicInteger();

    public AiJobWorker(RedisQueueService queueService, AiServiceImpl aiServiceImpl) {
        this.queueService = queueService;
        this.aiServiceImpl = aiServiceImpl;
    }

    @PostConstruct
    void start() {
        executor = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
            Thread t = new Thread(r, "ai-job-worker-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < WORKER_THREADS; i++) {
            executor.submit(this::pollLoop);
        }
        log.info("AiJobWorker started with {} thread(s), polling queue '{}'", WORKER_THREADS, QUEUE_NAME);
    }

    private void pollLoop() {
        while (running) {
            try {
                QueuedJob job = queueService.dequeueBlocking(QUEUE_NAME, POLL_TIMEOUT_SECONDS);
                if (job == null) continue; // timeout — loop back around so `running` gets re-checked
                process(job);
            } catch (Exception e) {
                log.error("Unexpected error in AI job worker poll loop — continuing", e);
            }
        }
    }

    private void process(QueuedJob job) {
        try {
            dispatch(job);
        } catch (Exception e) {
            queueService.deadLetter(QUEUE_NAME, job, e);
        }
    }

    private void dispatch(QueuedJob job) throws Exception {
        switch (job.getType()) {
            case "SCAN_PROJECT_FRAUD" -> {
                var payload = mapper.readValue(job.getPayloadJson(), AiServiceImpl.FraudScanPayload.class);
                aiServiceImpl.scanProjectForFraud(payload.projectId());
            }
            case "SCAN_KYC_DOCUMENT" -> {
                var payload = mapper.readValue(job.getPayloadJson(), AiServiceImpl.KycScanPayload.class);
                aiServiceImpl.scanKycDocument(payload.kycDocumentId());
            }
            case "SCAN_PROJECT_MODERATION" -> {
                var payload = mapper.readValue(job.getPayloadJson(), AiServiceImpl.ProjectModerationPayload.class);
                aiServiceImpl.scanProjectModeration(payload.projectId());
            }
            case "SCAN_COMMENT" -> {
                var payload = mapper.readValue(job.getPayloadJson(), AiServiceImpl.CommentModerationPayload.class);
                aiServiceImpl.scanCommentModeration(payload.commentId());
            }
            default -> log.error("Unknown AI job type '{}' — moving to dead-letter", job.getType());
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(POLL_TIMEOUT_SECONDS + 2L, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("AiJobWorker stopped");
    }
}
