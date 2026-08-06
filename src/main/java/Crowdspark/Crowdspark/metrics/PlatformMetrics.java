// src/main/java/Crowdspark/Crowdspark/metrics/PlatformMetrics.java
// Feature #31 — Health checks & monitoring
//
// Actuator + micrometer-registry-prometheus already expose generic JVM/HTTP
// metrics for free once added to the classpath. This class adds the
// business-specific ones the feature spec calls out explicitly: "Grafana
// dashboard JSON for project/donation metrics".
//
// Two different metric shapes are used on purpose:
//   - Gauges (current state, can go up OR down) are registered once here
//     with a live supplier function. Micrometer calls that supplier itself
//     every time Prometheus scrapes /actuator/prometheus, so these always
//     reflect the current DB state with no polling/scheduling needed.
//   - Counters (cumulative totals that only ever increase) are incremented
//     by calling code at the exact moment the underlying business event
//     commits, because a live COUNT query can't answer "how many happened in
//     the last 5 minutes" the way Prometheus's rate()/increase() can over a
//     true counter.

package Crowdspark.Crowdspark.metrics;

import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformMetrics {

    private final MeterRegistry registry;
    private final ProjectRepository projectRepository;

    private Counter donationsSucceededCounter;
    private Counter donationsAmountCounter;
    private Counter projectsSubmittedCounter;
    private Counter refundsCompletedCounter;

    @PostConstruct
    void registerMetrics() {

        // ── Gauges: current state, queried live on every scrape ────────────────
        Gauge.builder("crowdspark_projects_active", projectRepository,
                        r -> r.countByStatus(ProjectStatus.APPROVED))
                .description("Currently live projects accepting donations")
                .register(registry);

        Gauge.builder("crowdspark_projects_funded", projectRepository,
                        r -> r.countByStatus(ProjectStatus.FUNDED))
                .description("Projects that reached their funding goal")
                .register(registry);

        Gauge.builder("crowdspark_projects_failed", projectRepository,
                        r -> r.countByStatus(ProjectStatus.FAILED))
                .description("Projects whose deadline passed without reaching goal")
                .register(registry);

        Gauge.builder("crowdspark_projects_pending_review", projectRepository,
                        r -> r.countByStatus(ProjectStatus.PENDING))
                .description("Projects awaiting admin approval")
                .register(registry);

        Gauge.builder("crowdspark_funds_raised_inr", projectRepository,
                        ProjectRepository::sumCurrentAmountAcrossLiveProjects)
                .description("Total INR currently raised across approved/funded projects")
                .register(registry);

        // ── Counters: incremented by calling code at the moment of the event ───
        donationsSucceededCounter = Counter.builder("crowdspark_donations_succeeded_total")
                .description("Total donations that completed successfully")
                .register(registry);

        donationsAmountCounter = Counter.builder("crowdspark_donations_amount_inr_total")
                .description("Total INR successfully donated, cumulative")
                .register(registry);

        projectsSubmittedCounter = Counter.builder("crowdspark_projects_submitted_total")
                .description("Total projects submitted for approval")
                .register(registry);

        refundsCompletedCounter = Counter.builder("crowdspark_refunds_completed_total")
                .description("Total individual donation refunds completed")
                .register(registry);
    }

    /** Called from PaymentServiceImpl.confirmDonationPaid — the single,
     *  race-proof point (see Feature #1/#4 locking fix) where a donation is
     *  confirmed paid, so this can never double-count the same donation. */
    public void recordSuccessfulDonation(double amount) {
        donationsSucceededCounter.increment();
        donationsAmountCounter.increment(amount);
    }

    /** Called from ProjectServiceImpl.createProject. */
    public void recordProjectSubmitted() {
        projectsSubmittedCounter.increment();
    }

    /** Called from RefundTransactionExecutor.processSingleRefund on success. */
    public void recordRefundCompleted() {
        refundsCompletedCounter.increment();
    }
}
