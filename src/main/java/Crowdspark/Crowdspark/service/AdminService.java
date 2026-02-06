package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.AdminProjectListResponse;

import java.util.List;

public interface AdminService {

    List<AdminProjectListResponse> getPendingProjects();

    void approveProject(Long projectId);

    void rejectProject(Long projectId, String reason);

}
