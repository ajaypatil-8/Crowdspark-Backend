// src/main/java/Crowdspark/Crowdspark/service/AnalyticsService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.ProjectAnalyticsResponse;

public interface AnalyticsService {

    /**
     * Records a page view for a project.
     * Called by the frontend on every project detail page load.
     * Uses Redis HyperLogLog for unique visitor tracking.
     * Persists to DB via upsert — safe to call concurrently.
     *
     * @param projectId  project being viewed
     * @param visitorKey  hashed IP + User-Agent (for unique tracking)
     */
    void recordView(Long projectId, String visitorKey);

    /**
     * Returns full analytics for a project.
     * Only the project creator may call this.
     */
    ProjectAnalyticsResponse getAnalytics(Long projectId, Long requestingUserId);

    /**
     * Nightly job: flushes Redis unique visitor counts to PostgreSQL.
     * Called by @Scheduled in AnalyticsServiceImpl.
     */
    void flushUniquesToDb();
}
