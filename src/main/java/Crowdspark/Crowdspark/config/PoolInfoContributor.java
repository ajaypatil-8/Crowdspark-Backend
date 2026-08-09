// src/main/java/Crowdspark/Crowdspark/config/PoolInfoContributor.java
// Feature #37 — HikariCP + Redis connection pool tuning: pool metrics/info
//
// HikariCP's live metrics (hikaricp.connections.active/idle/pending/etc.)
// are already automatic in /actuator/metrics and /actuator/prometheus —
// Spring Boot wires HikariDataSource straight to any available MeterRegistry
// bean the moment Actuator + Micrometer are both present, which Feature #31
// already added. Nothing to write for that half.
//
// Lettuce's connection pool doesn't get the same fully-automatic treatment.
// Rather than hand-wire something to reach into Lettuce/commons-pool2
// internals for LIVE active/idle counts — real but harder to get right
// without being able to compile-test it — this contributes the RESOLVED
// pool configuration to /actuator/info instead: a low-risk way to let
// anyone confirm exactly what pool sizes are actually in effect on a given
// running instance (dev defaults vs application-prod.properties' larger
// ones) without SSHing in to check env vars or properties files by hand.

package Crowdspark.Crowdspark.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PoolInfoContributor implements InfoContributor {

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private int dbPoolMax;

    @Value("${spring.datasource.hikari.minimum-idle}")
    private int dbPoolMinIdle;

    @Value("${spring.data.redis.lettuce.pool.max-active}")
    private int redisPoolMaxActive;

    @Value("${spring.data.redis.lettuce.pool.min-idle}")
    private int redisPoolMinIdle;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("connectionPools", Map.of(
                "database", Map.of(
                        "maxPoolSize", dbPoolMax,
                        "minIdle", dbPoolMinIdle,
                        "liveMetrics", "/actuator/metrics/hikaricp.connections.active (and .idle, .pending, etc.)"
                ),
                "redis", Map.of(
                        "maxActive", redisPoolMaxActive,
                        "minIdle", redisPoolMinIdle
                )
        ));
    }
}
