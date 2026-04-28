package Crowdspark.Crowdspark.service;

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
}
