package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CreateProjectRequest;
import Crowdspark.Crowdspark.dto.ProjectResponse;

import java.util.List;

public interface ProjectService {

    ProjectResponse createProject(CreateProjectRequest request);

    List<ProjectResponse> getApprovedProjects(int page, int size);

    List<ProjectResponse> getMyProjects();

    ProjectResponse approveProject(Long projectId);

    ProjectResponse rejectProject(Long projectId);

    ProjectResponse assignCategories(Long projectId, List<Long> categoryIds);

}
