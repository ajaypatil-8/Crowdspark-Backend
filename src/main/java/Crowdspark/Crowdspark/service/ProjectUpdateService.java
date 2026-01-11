package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CreateProjectUpdateRequest;
import Crowdspark.Crowdspark.dto.ProjectUpdateResponse;

import java.util.List;

public interface ProjectUpdateService {

    ProjectUpdateResponse addUpdate(Long projectId, CreateProjectUpdateRequest request);

    List<ProjectUpdateResponse> getProjectUpdates(Long projectId);
}
