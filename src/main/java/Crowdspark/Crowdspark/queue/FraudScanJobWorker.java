// src/main/java/Crowdspark/Crowdspark/queue/FraudScanJobWorker.java
// Feature #43 — AI Fraud & Risk Detection
//
// Same structural pattern as EmailJobWorker (Feature #36): a small pool of
// daemon threads BRPOP-ing a Redis queue in a loop, dispatching by job type,
// dead-lettering anything that throws. Only one job type here so dispatch()
// is a single check rather than EmailJobWorker's switch, and one thread is
// plenty — this fires once per campaign submission, not per request.
//
// Depends on the CONCRETE AiServiceImpl class, not the AiService interface —
// scanProjectForFraud() is deliberately not part of the public interface,
// exactly like EmailJobWorker's relationship to EmailServiceImpl's "...Now"
// methods.

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
public class FraudScanJobWorker {

    private static final String QUEUE_NAME = "ai-fraud-scan";
    private static final int WORKER_THREADS = 1;
    private static final int POLL_TIMEOUT_SECONDS = 5;

    private final RedisQueueService queueService;
    private final AiServiceImpl     aiServiceImpl;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private ExecutorService executor;
    private volatile boolean running = true;
    private final AtomicInteger threadCounter = new AtomicInteger();

    public FraudScanJobWorker(RedisQueueService queueService, AiServiceImpl aiServiceImpl) {
        this.queueService = queueService;
        this.aiServiceImpl = aiServiceImpl;
    }

    @PostConstruct
    void start() {
        executor = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
            Thread t = new Thread(r, "fraud-scan-worker-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < WORKER_THREADS; i++) {
            executor.submit(this::pollLoop);
        }
        log.info("FraudScanJobWorker started with {} thread(s), polling queue '{}'", WORKER_THREADS, QUEUE_NAME);
    }

    private void pollLoop() {
        while (running) {
            try {
                QueuedJob job = queueService.dequeueBlocking(QUEUE_NAME, POLL_TIMEOUT_SECONDS);
                if (job == null) continue; // timeout — loop back around so `running` gets re-checked
                process(job);
            } catch (Exception e) {
                log.error("Unexpected error in fraud-scan worker poll loop — continuing", e);
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
        if (!"SCAN_PROJECT".equals(job.getType())) {
            log.error("Unknown fraud-scan job type '{}' — moving to dead-letter", job.getType());
            return;
        }
        var payload = mapper.readValue(job.getPayloadJson(), AiServiceImpl.FraudScanPayload.class);
        aiServiceImpl.scanProjectForFraud(payload.projectId());
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
        log.info("FraudScanJobWorker stopped");
    }
}
