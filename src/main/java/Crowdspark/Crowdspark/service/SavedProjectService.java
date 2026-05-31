// src/main/java/Crowdspark/Crowdspark/service/SavedProjectService.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.ProjectFeedResponse;

import java.util.List;

public interface SavedProjectService {

    /** Save a project — idempotent (safe to call twice) */
    void save(Long userId, Long projectId);

    /** Unsave a project — idempotent */
    void unsave(Long userId, Long projectId);

    /** Toggle save state, returns true if now saved */
    boolean toggle(Long userId, Long projectId);

    /** Get all saved projects for a user (newest first) */
    List<ProjectFeedResponse> getSaved(Long userId);

    /** Check if a user has saved a specific project */
    boolean isSaved(Long userId, Long projectId);
}
