package Crowdspark.Crowdspark.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    /*
     * BUG 5 FIX: Wrapped Redis CacheManager creation in try-catch.
     * Previously if Redis was not running at startup, Spring would throw
     * a connection exception and crash the entire application context.
     * Now it logs a WARNING and falls back to an in-memory cache so the
     * app starts successfully without Redis.
     *
     * NOTE: If Redis is required for token blacklisting or session management
     * in production, remove the fallback and fix the Redis connection instead.
     */
    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        try {
            // Test connection eagerly so we know immediately if Redis is down
            factory.getConnection().ping();

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.activateDefaultTyping(
                    LaissezFaireSubTypeValidator.instance,
                    ObjectMapper.DefaultTyping.NON_FINAL,
                    JsonTypeInfo.As.PROPERTY
            );

            GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

            RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                    .disableCachingNullValues();

            Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                    "categories",     defaults.entryTtl(Duration.ofHours(6)),
                    "projectDetails", defaults.entryTtl(Duration.ofMinutes(10)),
                    "exploreFeed",    defaults.entryTtl(Duration.ofMinutes(5))
            );

            log.info("Redis connection established — using RedisCacheManager");
            return RedisCacheManager.builder(factory)
                    .cacheDefaults(defaults.entryTtl(Duration.ofMinutes(5)))
                    .withInitialCacheConfigurations(cacheConfigs)
                    .build();

        } catch (Exception e) {
            log.warn(
                "⚠️  Redis is unavailable ({}). Falling back to in-memory cache. " +
                "Caching will not persist across restarts. Fix Redis for production.",
                e.getMessage()
            );
            // Fallback: simple ConcurrentMap-based cache (no TTL, in-process only)
            return new ConcurrentMapCacheManager("categories", "projectDetails", "exploreFeed");
        }
    }
}
