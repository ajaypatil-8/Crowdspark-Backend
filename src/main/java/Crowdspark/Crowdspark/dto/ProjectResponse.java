package Crowdspark.Crowdspark.dto;

import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectResponse {

    private Long id;
    private String title;
    private String description;
    private Double goalAmount;
    private Double currentAmount;
    private ProjectStatus status;
    private Long creatorId;
    private LocalDateTime createdAt;

    private List<CategoryResponse> categories;

    private List<ProjectMediaResponse> media;



}
