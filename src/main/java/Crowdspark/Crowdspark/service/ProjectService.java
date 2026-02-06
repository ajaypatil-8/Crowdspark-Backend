package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CreateProjectRequest;
import Crowdspark.Crowdspark.dto.CreatorProjectResponse;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;

import java.util.List;

public interface ProjectService {

    Long createProject(CreateProjectRequest request, Long creatorId);

    List<ProjectFeedResponse> getProjectFeed();

    List<CreatorProjectResponse> getCreatorProjects(Long creatorId);

    ProjectFullDetailsResponse getProjectDetails(Long projectId);

}
