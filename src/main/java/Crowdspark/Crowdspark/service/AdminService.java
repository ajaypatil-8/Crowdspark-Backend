package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.AdminFlaggedCommentResponse;
import Crowdspark.Crowdspark.dto.AdminProjectListResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;
import Crowdspark.Crowdspark.dto.UserResponse;


import java.util.List;

public interface AdminService {



    ProjectFullDetailsResponse getProjectDetail(Long projectId);

    List<AdminProjectListResponse> getPendingProjects();

    List<AdminProjectListResponse> getAllProjects();      // ✅ NEW

    void approveProject(Long projectId);

    void rejectProject(Long projectId, String reason);

    List<UserResponse> getAllUsers();                     // ✅ NEW

    void suspendUser(Long userId);                       // ✅ NEW

    void activateUser(Long userId);                      // ✅ NEW

    // Feature #45 — AI Content Moderation: comments only (flagged projects
    // already appear in getPendingProjects()/getAllProjects() above)
    List<AdminFlaggedCommentResponse> getFlaggedComments();

    /** restore=true un-hides the comment (deleted=false); restore=false
     *  leaves it hidden. Either way marks the flag resolvedByAdmin=true so
     *  it drops out of getFlaggedComments(). */
    void resolveFlaggedComment(Long checkId, boolean restore);
}
