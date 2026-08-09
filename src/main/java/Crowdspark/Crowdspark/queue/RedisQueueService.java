// src/main/java/Crowdspark/Crowdspark/queue/RedisQueueService.java
// Feature #36 — Async job queue
//
// A plain Redis LIST used as a queue: enqueue = LPUSH, dequeue = BRPOP
// (blocking right-pop — waits for a new item instead of busy-polling).
// Deliberately simple over something like Redis Streams: this app has a
// handful of well-known job types and no need for consumer groups or replay,
// so a list keeps this easy to reason about and inspect (LRANGE any queue
// key directly in redis-cli to see what's pending).
//
// IMPORTANT: this project's OWN RedisConfig already establishes the rule
// that Redis being unavailable must never crash the app or break a feature
// that used to work without Redis — it falls back to an in-memory cache
// rather than fail startup. Before this feature, email sending had NO
// dependency on Redis at all. Tying it to Redis without a fallback would be
// a real regression: send an OTP the moment Redis happens to be down, and
// the user gets nothing. So enqueue() takes a `fallback` — if the Redis push
// itself fails, the fallback runs on a small dedicated executor instead
// (still non-blocking for the caller, just skipping the durability the
// queue would otherwise provide for that one job).

package Crowdspark.Crowdspark.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class RedisQueueService {

    private static final String QUEUE_KEY_PREFIX = "crowdspark:queue:";
    private static final String DEAD_LETTER_SUFFIX = ":dead-letter";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper mapper;
    private final Executor fallbackExecutor;

    public RedisQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        // findAndRegisterModules() picks up JavaTimeModule (jackson-datatype-jsr310
        // is already a dependency here) so LocalDateTime fields round-trip correctly.
        // Without it, a fresh `new ObjectMapper()` throws on any LocalDateTime field —
        // exactly the kind of thing that only shows up the first time a receipt
        // email (which carries a LocalDateTime paidAt) actually gets queued.
        this.mapper = new ObjectMapper().findAndRegisterModules();
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("queue-fallback-");
        executor.initialize();
        this.fallbackExecutor = executor;
    }

    /**
     * Serializes payload, wraps it with `type` in a QueuedJob envelope, and
     * LPUSHes it onto queueName. If the push itself fails (Redis down,
     * network issue), runs `fallback` on a small dedicated executor instead —
     * still off the caller's thread, just without the durability a Redis
     * outage already took away for this one job.
     */
    public void enqueue(String queueName, String type, Object payload, Runnable fallback) {
        try {
            String payloadJson = mapper.writeValueAsString(payload);
            String jobJson = mapper.writeValueAsString(new QueuedJob(type, payloadJson));
            redisTemplate.opsForList().leftPush(QUEUE_KEY_PREFIX + queueName, jobJson);
        } catch (Exception e) {
            log.warn("Redis queue '{}' unavailable for job type '{}' — running directly instead: {}",
                    queueName, type, e.getMessage());
            fallbackExecutor.execute(fallback);
        }
    }

    /**
     * Blocks up to timeoutSeconds waiting for a job on queueName; returns
     * null on timeout (lets a worker's poll loop periodically check whether
     * it should shut down, rather than blocking forever on one call).
     */
    public QueuedJob dequeueBlocking(String queueName, int timeoutSeconds) {
        String json = redisTemplate.opsForList()
                .rightPop(QUEUE_KEY_PREFIX + queueName, Duration.ofSeconds(timeoutSeconds));
        if (json == null) return null;
        try {
            return mapper.readValue(json, QueuedJob.class);
        } catch (Exception e) {
            log.error("Malformed job on queue '{}', moving straight to dead-letter: {}", queueName, json, e);
            redisTemplate.opsForList().leftPush(QUEUE_KEY_PREFIX + queueName + DEAD_LETTER_SUFFIX, json);
            return null;
        }
    }

    /** Deserializes payloadJson into the given type. Thrown exceptions are the caller's to handle (see dead-letter below). */
    public <T> T readPayload(QueuedJob job, Class<T> type) throws Exception {
        return mapper.readValue(job.getPayloadJson(), type);
    }

    /** Call when a dequeued job fails processing (not malformed — just threw). Logged and preserved for manual inspection rather than silently dropped or retried forever. */
    public void deadLetter(String queueName, QueuedJob job, Exception cause) {
        try {
            String jobJson = mapper.writeValueAsString(job);
            redisTemplate.opsForList().leftPush(QUEUE_KEY_PREFIX + queueName + DEAD_LETTER_SUFFIX, jobJson);
        } catch (Exception e) {
            log.error("Failed to write job to dead-letter queue '{}' (job lost): {}", queueName, job, e);
        }
        log.error("Job moved to dead-letter queue '{}{}': type={} — {}",
                queueName, DEAD_LETTER_SUFFIX, job.getType(), cause.getMessage(), cause);
    }
}
