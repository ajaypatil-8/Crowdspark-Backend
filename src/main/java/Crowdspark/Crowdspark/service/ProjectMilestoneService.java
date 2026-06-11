// src/main/java/Crowdspark/Crowdspark/service/ProjectMilestoneService.java

package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.MilestoneRequest;
import Crowdspark.Crowdspark.dto.MilestoneResponse;

import java.util.List;

public interface ProjectMilestoneService {

    /**
     * GET /api/projects/{id}/milestones
     * Public — returns all milestones ordered by sort_order.
     */
    List<MilestoneResponse> getMilestones(Long projectId);

    /**
     * POST /api/projects/{id}/milestones
     * Creator only — add a new milestone.
     * Appended at the end (or at the requested sort_order position).
     * Max 20 milestones per project.
     */
    MilestoneResponse createMilestone(Long projectId,
                                      MilestoneRequest request,
                                      Long creatorId);

    /**
     * PUT /api/projects/{id}/milestones/{milestoneId}
     * Creator only — edit title, description, targetAmount, or sortOrder.
     */
    MilestoneResponse updateMilestone(Long projectId,
                                      Long milestoneId,
                                      MilestoneRequest request,
                                      Long creatorId);

    /**
     * DELETE /api/projects/{id}/milestones/{milestoneId}
     * Creator only — remove a milestone.
     */
    void deleteMilestone(Long projectId, Long milestoneId, Long creatorId);

    /**
     * POST /api/projects/{id}/milestones/{milestoneId}/complete
     * Creator only — mark as completed, fires notification to all backers.
     * Idempotent: marking an already-completed milestone is a no-op.
     */
    MilestoneResponse completeMilestone(Long projectId, Long milestoneId, Long creatorId);

    /**
     * POST /api/projects/{id}/milestones/{milestoneId}/reopen
     * Creator only — revert a completed milestone back to PENDING.
     */
    MilestoneResponse reopenMilestone(Long projectId, Long milestoneId, Long creatorId);
}
