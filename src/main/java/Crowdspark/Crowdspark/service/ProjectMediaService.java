package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.AddProjectMediaRequest;
import Crowdspark.Crowdspark.dto.ProjectMediaResponse;

import java.util.List;

public interface ProjectMediaService {

    ProjectMediaResponse addMedia(Long projectId, AddProjectMediaRequest request);

    void deleteMedia(Long mediaId);

    List<ProjectMediaResponse> getProjectMedia(Long projectId);
}
