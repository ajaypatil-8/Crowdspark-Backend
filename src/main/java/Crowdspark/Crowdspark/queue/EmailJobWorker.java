// src/main/java/Crowdspark/Crowdspark/queue/EmailJobWorker.java
// Feature #36 — Async job queue
//
// A small pool of daemon threads, each running BRPOP against the "email"
// Redis queue in a loop. On a job, deserializes payloadJson into the
// matching typed record (see EmailServiceImpl's nested payload records) and
// calls the corresponding "Now" method — the exact same logic that used to
// run directly under @Async, just invoked from here instead.
//
// Depends on the CONCRETE EmailServiceImpl class, not the EmailService
// interface — the "Now" methods are implementation details the worker needs
// direct access to; they're deliberately not part of the public interface
// other callers use.

package Crowdspark.Crowdspark.queue;

import Crowdspark.Crowdspark.service.impl.EmailServiceImpl;
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
public class EmailJobWorker {

    private static final String QUEUE_NAME = "email";
    private static final int WORKER_THREADS = 3;
    private static final int POLL_TIMEOUT_SECONDS = 5;

    private final RedisQueueService queueService;
    private final EmailServiceImpl emailServiceImpl;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private ExecutorService executor;
    private volatile boolean running = true;
    private final AtomicInteger threadCounter = new AtomicInteger();

    public EmailJobWorker(RedisQueueService queueService, EmailServiceImpl emailServiceImpl) {
        this.queueService = queueService;
        this.emailServiceImpl = emailServiceImpl;
    }

    @PostConstruct
    void start() {
        executor = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
            Thread t = new Thread(r, "email-worker-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < WORKER_THREADS; i++) {
            executor.submit(this::pollLoop);
        }
        log.info("EmailJobWorker started with {} threads, polling queue '{}'", WORKER_THREADS, QUEUE_NAME);
    }

    private void pollLoop() {
        while (running) {
            try {
                QueuedJob job = queueService.dequeueBlocking(QUEUE_NAME, POLL_TIMEOUT_SECONDS);
                if (job == null) continue; // timeout — loop back around so `running` gets re-checked
                process(job);
            } catch (Exception e) {
                // Catches anything dequeueBlocking itself might throw (e.g. Redis connection
                // drop mid-poll) — logged and retried on the next loop iteration rather than
                // killing this worker thread permanently.
                log.error("Unexpected error in email worker poll loop — continuing", e);
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
            case "OTP" -> {
                var p = mapper.readValue(job.getPayloadJson(), EmailServiceImpl.OtpEmailPayload.class);
                emailServiceImpl.sendOtpEmailNow(p.toEmail(), p.name(), p.otp(), p.validityMinutes());
            }
            case "SIMPLE" -> {
                var p = mapper.readValue(job.getPayloadJson(), EmailServiceImpl.SimpleEmailPayload.class);
                emailServiceImpl.sendSimpleEmailNow(p.toEmail(), p.subject(), p.body());
            }
            case "WELCOME" -> {
                var p = mapper.readValue(job.getPayloadJson(), EmailServiceImpl.WelcomeEmailPayload.class);
                emailServiceImpl.sendWelcomeEmailNow(p.toEmail(), p.name());
            }
            case "CAMPAIGN_APPROVED" -> {
                var p = mapper.readValue(job.getPayloadJson(), EmailServiceImpl.CampaignApprovedPayload.class);
                emailServiceImpl.sendCampaignApprovedEmailNow(p.toEmail(), p.creatorName(), p.projectTitle(), p.projectId());
            }
            case "CAMPAIGN_REJECTED" -> {
                var p = mapper.readValue(job.getPayloadJson(), EmailServiceImpl.CampaignRejectedPayload.class);
                emailServiceImpl.sendCampaignRejectedEmailNow(p.toEmail(), p.creatorName(), p.projectTitle(), p.reason());
            }
            case "CAMPAIGN_FUNDED" -> {
                var p = mapper.readValue(job.getPayloadJson(), EmailServiceImpl.CampaignFundedPayload.class);
                emailServiceImpl.sendCampaignFundedEmailNow(p.toEmail(), p.creatorName(), p.projectTitle(),
                        p.projectId(), p.raisedAmount(), p.goalAmount());
            }
            case "BACKER_RECEIPT" -> {
                var p = mapper.readValue(job.getPayloadJson(), EmailServiceImpl.BackerReceiptPayload.class);
                emailServiceImpl.sendBackerReceiptEmailNow(p.toEmail(), p.backerName(), p.projectTitle(),
                        p.projectId(), p.donationId(), p.amount(), p.transactionId(), p.rewardTierTitle(), p.paidAt());
            }
            default -> log.error("Unknown email job type '{}' — moving to dead-letter", job.getType());
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
        log.info("EmailJobWorker stopped");
    }
}
