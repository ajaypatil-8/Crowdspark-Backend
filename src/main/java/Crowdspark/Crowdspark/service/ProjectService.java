package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CreateProjectRequest;

public interface ProjectService {

    Long createProject(CreateProjectRequest request, Long creatorId);
}
